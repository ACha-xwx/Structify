package com.feng.dsagent.chat;

public record PendingChat(String sessionId, long messageId, ChatCommand command, ChatRetry retry) {
}
