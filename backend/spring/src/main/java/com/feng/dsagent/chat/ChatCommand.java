package com.feng.dsagent.chat;

import java.util.List;

public record ChatCommand(String prompt, String chapterId, String sessionId, List<ChatTurn> history,
                          Boolean thinkingEnabled, String reasoningEffort, List<ChatAttachment> attachments,
                          Long retryMessageId) {

    public ChatCommand {
        history = history == null ? List.of() : List.copyOf(history);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public ChatCommand(String prompt, String chapterId, String sessionId, List<ChatTurn> history) {
        this(prompt, chapterId, sessionId, history, null, null, List.of(), null);
    }

    public ChatCommand(String prompt, String chapterId, String sessionId, List<ChatTurn> history,
                       Boolean thinkingEnabled, String reasoningEffort, List<ChatAttachment> attachments) {
        this(prompt, chapterId, sessionId, history, thinkingEnabled, reasoningEffort, attachments, null);
    }
}
