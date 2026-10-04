package com.feng.dsagent.chat;

import java.time.Instant;

record StoredChatAttachment(
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
