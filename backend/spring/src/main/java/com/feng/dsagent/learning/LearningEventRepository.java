package com.feng.dsagent.learning;

import java.time.Instant;

public interface LearningEventRepository {

    public boolean isPublishedChapter(String chapterId);

    public LearningEventView save(
        long userId,
        String eventType,
        String chapterId,
        String referenceId,
        String payloadJson,
        Instant createdAt
    );
}
