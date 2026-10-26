package com.feng.dsagent.chat.domain;

import java.util.List;

public record ChatRetry(long messageId, long lastMessageId, ChatCommand command, List<ChatTurn> history) {
}
