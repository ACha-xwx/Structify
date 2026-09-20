package com.feng.dsagent.presentation;

import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only courseware for the classroom's right-hand pane and the standalone deck browser.
 * Images are streamed by the backend so the reviewed decks never become a public static folder.
 */
@RestController
@RequestMapping("/api/v1/presentation")
public class PresentationController {

    private final PresentationService presentations;

    public PresentationController(PresentationService presentations) {
        this.presentations = presentations;
    }

    @GetMapping("/meta")
    PresentationService.Meta meta() {
        return presentations.meta();
    }

    @GetMapping("/lessons/{lessonId}/slides")
    PresentationService.LessonCourseware lessonSlides(@PathVariable String lessonId) {
        return presentations.forLesson(lessonId);
    }

    @GetMapping("/courseware/{coursewareKey}/slides")
    PresentationService.LessonCourseware coursewareSlides(@PathVariable String coursewareKey) {
        return presentations.forCoursewareKey(coursewareKey);
    }

    @GetMapping("/decks")
    List<PresentationCatalog.Deck> decks() {
        return presentations.decks();
    }

    @GetMapping("/decks/{deckId}/slides")
    List<PresentationSlide> deckSlides(@PathVariable String deckId) {
        return presentations.deckSlides(deckId);
    }

    @GetMapping("/slides/{slideId}/image")
    ResponseEntity<FileSystemResource> image(@PathVariable String slideId) {
        Path file = presentations.imageFile(slideId);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(new FileSystemResource(file));
    }
}
