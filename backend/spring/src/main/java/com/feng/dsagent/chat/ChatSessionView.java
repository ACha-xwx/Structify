package com.feng.dsagent.chat;

import java.time.Instant;
import java.util.List;

public record ChatSessionView(
    String id,
    String chapterId,
    String title,
    Instant updatedAt,
    List<ChatMessageView> messages,
    boolean pinned
) {

    public ChatSessionView(String id, String chapterId, String title, Instant updatedAt, List<ChatMessageView> messages) {
        this(id, chapterId, title, updatedAt, messages, false);
    }

    public ChatSessionView {
        messages = List.copyOf(messages);
    }
}
