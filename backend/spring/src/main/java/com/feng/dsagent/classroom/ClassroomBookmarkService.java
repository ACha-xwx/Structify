package com.feng.dsagent.classroom;

import com.feng.dsagent.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
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
class ClassroomBookmarkService {

    private final ClassroomBookmarkRepository bookmarks;
    private final ClassroomTimeline timeline;

    ClassroomBookmarkService(ClassroomBookmarkRepository bookmarks, ClassroomTimeline timeline) {
        this.bookmarks = bookmarks;
        this.timeline = timeline;
    }

    ClassroomBookmark save(long userId, String sessionId, int stepIndex) {
        ClassroomTimeline.StepPage page = timeline.stepPage(userId, sessionId, stepIndex);
        return bookmarks.save(userId, new ClassroomBookmarkDraft(page.sessionId(), page.lessonId(),
            page.lessonTitle(), page.chapterId(), page.stepIndex(), page.slideId(), page.title()));
    }

    List<ClassroomBookmark> list(long userId) {
        return bookmarks.listByUser(userId);
    }

    void delete(long userId, long id) {
        if (!bookmarks.delete(userId, id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLASSROOM_BOOKMARK_NOT_FOUND", "收藏不存在");
        }
    }
}
