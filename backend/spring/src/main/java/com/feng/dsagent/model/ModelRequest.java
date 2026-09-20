package com.feng.dsagent.model;

import java.util.List;
import java.util.Objects;

public record ModelRequest(List<ModelMessage> messages, Double temperature, Integer maxTokens, boolean jsonObject, boolean disableThinking) {

    public ModelRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }

    public ModelRequest(List<ModelMessage> messages) {
        this(messages, null, null);
    }

    public ModelRequest(List<ModelMessage> messages, Double temperature, Integer maxTokens) {
        this(messages, temperature, maxTokens, false, false);
    }
}
