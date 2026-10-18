package com.feng.dsagent.chat;

import java.util.List;

record ChatRetry(long messageId, long lastMessageId, ChatCommand command, List<ChatTurn> history) {
}
