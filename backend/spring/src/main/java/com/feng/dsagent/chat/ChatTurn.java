package com.feng.dsagent.chat;

public record ChatTurn(String role, String content, java.util.List<ChatAttachment> attachments) {
    public ChatTurn {
        attachments = attachments == null ? java.util.List.of() : java.util.List.copyOf(attachments);
    }
    public ChatTurn(String role, String content) { this(role, content, java.util.List.of()); }
}
