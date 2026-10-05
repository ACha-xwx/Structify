package com.feng.dsagent.chat;

import java.util.List;

public record ChatResponse(String answer, String sessionId, List<ChatSource> sources, boolean persisted,
                           String reasoning) {

    public ChatResponse {
        sources = List.copyOf(sources);
    }

    public ChatResponse(String answer, String sessionId, List<ChatSource> sources, boolean persisted) {
        this(answer, sessionId, sources, persisted, null);
    }
}
