package com.feng.dsagent.classroom.entity;

/**
 * One step of a lesson a learner saved, with the labels that step carried when it was saved.
 *
 * <p>The lesson and page identity are copied rather than looked up later: a bookmark is a note about a
 * moment that has already happened, and it has to keep reading correctly after the lesson is re-prepared
 * or the deck re-rendered.
 */
public record ClassroomBookmarkEntity(
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
