package com.feng.dsagent.presentation.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.presentation.support.PresentationCatalog;
import com.feng.dsagent.presentation.domain.PresentationSlide;
import com.feng.dsagent.classroom.support.TextbookLessonIds;
import com.feng.dsagent.presentation.mapper.PresentationMapper;
import java.util.List;

@Service
public class PresentationService {

    public record LessonCourseware(String lessonId, String coursewareKey, String title, String source, boolean ready, String builtAt, List<PresentationSlide> slides) {
    }

    public record Meta(boolean ready, String builtAt, int slideCount, int deckCount, int lessonCount) {
    }

    private final PresentationCatalog catalog;
    private final PresentationMapper presentations;

    public PresentationService(PresentationCatalog catalog, PresentationMapper presentations) {
        this.catalog = catalog;
        this.presentations = presentations;
    }

    public Meta meta() {
        PresentationCatalog.Snapshot snapshot = catalog.snapshot();
        return new Meta(snapshot.ready, snapshot.builtAt, snapshot.slides.size(), snapshot.decks().size(), snapshot.lessonOrder.size());
    }

    public LessonCourseware forLesson(String lessonId) {
        List<SourceLesson> lessons = textbookLessons();
        SourceLesson lesson = lessons.stream().filter(item -> item.id().equals(lessonId)).findFirst().orElse(null);
        if (lesson == null) {
            return new LessonCourseware(lessonId, "", "", "", false, catalog.snapshot().builtAt, List.of());
        }
        String key = TextbookLessonIds.coursewareKey(lesson.source());
        List<PresentationSlide> slides = catalog.slidesForCoursewareKey(key);
        return new LessonCourseware(lessonId, key, lesson.title(), lesson.source(), catalog.snapshot().ready, catalog.snapshot().builtAt, slides);
    }

    /** Courseware keyed by the lesson number, for callers that only know the reviewed file name. */
    public LessonCourseware forCoursewareKey(String key) {
        List<PresentationSlide> slides = catalog.slidesForCoursewareKey(key);
        return new LessonCourseware("", key, "", "", catalog.snapshot().ready, catalog.snapshot().builtAt, slides);
    }

    /** Ordered sub-lessons (with scenes and page annotations) of the lesson's courseware. */
    public List<PresentationCatalog.SubLessonPlan> subLessons(String lessonId) {
        return catalog.subLessons(forLesson(lessonId).coursewareKey());
    }

    public PresentationSlide slide(String slideId) {
        return catalog.slide(slideId);
    }

    public List<PresentationCatalog.Deck> decks() {
        return catalog.decks();
    }

    public List<PresentationSlide> deckSlides(String deckId) {
        return catalog.deckSlides(deckId);
    }

    public java.nio.file.Path imageFile(String slideId) {
        return catalog.imageFile(slideId);
    }

    private record SourceLesson(String id, String title, String source) {
    }

    /**
     * Same identity rule as classroom preparation: lesson id derived from chapter + reviewed source path,
     * so the browser can move from a classroom session straight to that lesson's slides.
     */
    private List<SourceLesson> textbookLessons() {
        return presentations.selectTextbookLessons().stream()
            .map(row -> new SourceLesson(
                TextbookLessonIds.of(row.chapterId(), row.sourcePath()),
                row.title(),
                row.sourcePath()))
            .distinct()
            .toList();
    }
}
