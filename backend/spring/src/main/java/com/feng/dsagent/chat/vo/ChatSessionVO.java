package com.feng.dsagent.chat.vo;

import java.time.Instant;
import java.util.List;

public record ChatSessionVO(
    String id,
    String chapterId,
    String title,
    Instant updatedAt,
    List<ChatMessageVO> messages,
    boolean pinned
) {

    public ChatSessionVO(String id, String chapterId, String title, Instant updatedAt, List<ChatMessageVO> messages) {
        this(id, chapterId, title, updatedAt, messages, false);
    }

    public ChatSessionVO {
        messages = List.copyOf(messages);
    }
}
