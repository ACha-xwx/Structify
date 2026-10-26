package com.feng.dsagent.classroom.service;

import com.feng.dsagent.classroom.converter.ClassroomBookmarkConverter;
import com.feng.dsagent.classroom.entity.ClassroomBookmarkEntity;
import com.feng.dsagent.classroom.mapper.ClassroomBookmarkMapper;
import com.feng.dsagent.classroom.support.ClassroomTimeline;
import com.feng.dsagent.classroom.vo.ClassroomBookmarkVO;
import com.feng.dsagent.classroom.error.ClassroomErrorCode;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Saving a step of a lesson so the learner can find it again.
 *
 * <p>Nothing here trusts what the client says the step is: the caller names a session and a step, and the
 * timeline - which owns the script and the courseware alignment - says which page that step shows and
 * what it is about. The session is read through the learner's own id, so a bookmark can only be made on a
 * lesson that learner is actually in.
 */
@Service
public class ClassroomBookmarkService {
    private final ClassroomBookmarkMapper bookmarks;
    private final ClassroomTimeline timeline;
    private final ClassroomBookmarkConverter classroomBookmarkConverter;

    public ClassroomBookmarkService(
        ClassroomBookmarkMapper bookmarks,
        ClassroomTimeline timeline,
        ClassroomBookmarkConverter classroomBookmarkConverter
    ) {
        this.bookmarks = bookmarks;
        this.timeline = timeline;
        this.classroomBookmarkConverter = classroomBookmarkConverter;
    }

    public ClassroomBookmarkVO save(long userId, String sessionId, int stepIndex) {
        ClassroomTimeline.StepPage page = timeline.stepPage(userId, sessionId, stepIndex);
        try {
            bookmarks.insert(
                userId,
                page.sessionId(),
                page.lessonId(),
                page.lessonTitle(),
                page.chapterId(),
                page.stepIndex(),
                page.slideId(),
                page.title()
            );
        } catch (DuplicateKeyException ignored) {
            // Same step already saved: return the existing bookmark.
        }
        ClassroomBookmarkEntity bookmark = bookmarks.findByStep(userId, page.sessionId(), page.stepIndex());
        if (bookmark == null) {
            throw new IllegalStateException("bookmark row missing after save");
        }
        return classroomBookmarkConverter.toView(bookmark);
    }

    public List<ClassroomBookmarkVO> list(long userId) {
        return bookmarks.listByUser(userId).stream().map(classroomBookmarkConverter::toView).toList();
    }

    public void delete(long userId, long id) {
        if (bookmarks.delete(userId, id) != 1) {
            throw ClassroomErrorCode.BOOKMARK_NOT_FOUND.toException();
        }
    }
}
