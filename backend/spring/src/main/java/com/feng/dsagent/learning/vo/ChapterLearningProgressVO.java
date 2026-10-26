package com.feng.dsagent.learning.vo;

import java.time.Instant;

public record ChapterLearningProgressVO(
    String chapterId,
    int chapterNumber,
    String title,
    long chatCount,
    long classroomCount,
    long animationCount,
    long codeRunCount,
    long eventCount,
    long totalActivities,
    Instant lastActivityAt
) {
}
