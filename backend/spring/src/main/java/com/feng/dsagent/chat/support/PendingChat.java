package com.feng.dsagent.chat.support;

import com.feng.dsagent.chat.domain.ChatCommand;
import com.feng.dsagent.chat.domain.ChatRetry;

public record PendingChat(String sessionId, long messageId, ChatCommand command, ChatRetry retry) {
}
