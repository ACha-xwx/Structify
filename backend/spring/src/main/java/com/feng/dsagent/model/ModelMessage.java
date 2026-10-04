package com.feng.dsagent.model;

import java.util.Objects;
import java.util.List;

public record ModelMessage(String role, String content, List<String> imageUrls) {

    public ModelMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    public ModelMessage(String role, String content) {
        this(role, content, List.of());
    }
}
