package com.feng.dsagent.chat.domain;

public record ChatAttachmentFile(String name, String mimeType, byte[] bytes) {
}
