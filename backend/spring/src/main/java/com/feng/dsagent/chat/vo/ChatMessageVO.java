package com.feng.dsagent.chat.vo;

import java.time.Instant;
import java.util.List;

import com.feng.dsagent.chat.domain.ChatAttachment;
import com.feng.dsagent.chat.domain.ChatSource;
public record ChatMessageVO(
    long id,
    String role,
    String content,
    List<ChatSource> sources,
    Instant createdAt,
    List<ChatAttachment> attachments,
    String reasoning,
    String chapterId,
    Boolean thinkingEnabled,
    String reasoningEffort,
    String generationStatus,
    String failureCode
) {

    public ChatMessageVO {
        sources = List.copyOf(sources);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
    public ChatMessageVO(long id, String role, String content, List<ChatSource> sources, Instant createdAt) {
        this(id, role, content, sources, createdAt, List.of(), null, null, null, null, null, null);
    }
    public ChatMessageVO(long id, String role, String content, List<ChatSource> sources, Instant createdAt,
                           List<ChatAttachment> attachments) {
        this(id, role, content, sources, createdAt, attachments, null, null, null, null, null, null);
    }
}
