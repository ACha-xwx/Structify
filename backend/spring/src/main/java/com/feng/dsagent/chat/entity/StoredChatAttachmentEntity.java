package com.feng.dsagent.chat.entity;

import java.time.Instant;

public record StoredChatAttachmentEntity(
    String id,
    long userId,
    long messageId,
    String name,
    String type,
    String mimeType,
    String encoding,
    long byteSize,
    String sha256,
    String storageKey,
    Instant createdAt
) {
}
