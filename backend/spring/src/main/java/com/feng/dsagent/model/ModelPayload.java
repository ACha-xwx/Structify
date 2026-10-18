package com.feng.dsagent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared wire format for the environment and persisted-endpoint transports. */
public final class ModelPayload {
    private ModelPayload() {}

    public static List<Map<String, Object>> messages(ModelRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ModelMessage message : request.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role());
            if (message.imageUrls().isEmpty()) {
                item.put("content", message.content());
            } else {
                List<Map<String, Object>> parts = new ArrayList<>();
                parts.add(Map.of("type", "text", "text", message.content()));
                for (String url : message.imageUrls()) {
                    parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
                }
                item.put("content", parts);
            }
            messages.add(item);
        }
        return messages;
    }

    public static boolean thinking(Map<String, Object> payload, ModelRequest request, String provider, boolean defaultDisabled) {
        if (!"deepseek".equalsIgnoreCase(provider)) return false;
        if (request.modelOverride() != null) payload.put("model", request.modelOverride());
        Boolean enabled = request.thinkingEnabled();
        if (enabled != null) {
            payload.put("thinking", Map.of("type", enabled ? "enabled" : "disabled"));
            if (enabled) {
                String effort = request.reasoningEffort() == null ? "high" : request.reasoningEffort();
                if (!List.of("low", "high", "max").contains(effort)) throw new IllegalArgumentException("Invalid reasoning effort");
                payload.put("reasoning_effort", effort);
            }
            return enabled;
        }
        if (request.disableThinking() || defaultDisabled) payload.put("thinking", Map.of("type", "disabled"));
        return false;
    }
}
