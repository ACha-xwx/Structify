package com.feng.dsagent.chat;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.chat.attachments")
public record ChatAttachmentProperties(String directory) {

    public Path root() {
        String configured = directory == null || directory.isBlank()
            ? "../../private/chat-attachments"
            : directory;
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
