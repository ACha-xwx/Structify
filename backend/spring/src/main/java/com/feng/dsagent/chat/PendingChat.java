package com.feng.dsagent.chat;

record PendingChat(String sessionId, long messageId, ChatCommand command, ChatRetry retry) {
}
