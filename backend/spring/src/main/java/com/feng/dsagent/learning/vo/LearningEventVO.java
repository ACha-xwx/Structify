package com.feng.dsagent.learning.vo;

import java.time.Instant;

public record LearningEventVO(
    long id,
    String eventType,
    String chapterId,
    String referenceId,
    Instant createdAt
) {
}
