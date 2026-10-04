package com.feng.dsagent.chat;

import java.time.Instant;

public record ChatSessionSummary(
    String id,
    String chapterId,
    String title,
    Instant updatedAt,
    long messageCount,
    boolean pinned
) {

    public ChatSessionSummary(String id, String chapterId, String title, Instant updatedAt, long messageCount) {
        this(id, chapterId, title, updatedAt, messageCount, false);
    }
}
