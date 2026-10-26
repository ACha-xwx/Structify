package com.feng.dsagent.chat.vo;

import java.util.List;

import com.feng.dsagent.chat.domain.ChatSource;
public record ChatVO(String answer, String sessionId, List<ChatSource> sources, boolean persisted,
                           String reasoning) {

    public ChatVO {
        sources = List.copyOf(sources);
    }

    public ChatVO(String answer, String sessionId, List<ChatSource> sources, boolean persisted) {
        this(answer, sessionId, sources, persisted, null);
    }
}
