package com.feng.dsagent.model;

/**
 * Provider-reported token usage is optional because some OpenAI-compatible providers omit it.
 */
public record ModelResponse(String content, Long totalTokens, String finishReason) {

    public ModelResponse(String content, Long totalTokens) {
        this(content, totalTokens, null);
    }

    public ModelResponse(String content) {
        this(content, null, null);
    }
}
