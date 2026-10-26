package com.feng.dsagent.classroom.vo;

/**
 * One saved lesson step returned to the learner, with labels frozen at save time.
 */
public record ClassroomBookmarkVO(
    long id,
    String sessionId,
    String lessonId,
    String lessonTitle,
    String chapterId,
    int stepIndex,
    String slideId,
    String title,
    String createdAt
) {
}
