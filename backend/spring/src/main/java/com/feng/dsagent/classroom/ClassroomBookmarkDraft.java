package com.feng.dsagent.classroom;


public record ClassroomBookmarkDraft(
    String sessionId,
    String lessonId,
    String lessonTitle,
    String chapterId,
    int stepIndex,
    String slideId,
    String title
) {
}
