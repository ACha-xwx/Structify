package com.feng.dsagent.chat.vo;

import java.time.Instant;

public record ChatSessionSummaryVO(
    String id,
    String chapterId,
    String title,
    Instant updatedAt,
    long messageCount,
    boolean pinned
) {

    public ChatSessionSummaryVO(String id, String chapterId, String title, Instant updatedAt, long messageCount) {
        this(id, chapterId, title, updatedAt, messageCount, false);
    }
}
