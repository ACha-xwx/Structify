package com.feng.dsagent.classroom.dto;


public record ClassroomBookmarkDraftDTO(
    String sessionId,
    String lessonId,
    String lessonTitle,
    String chapterId,
    int stepIndex,
    String slideId,
    String title
) {
}
