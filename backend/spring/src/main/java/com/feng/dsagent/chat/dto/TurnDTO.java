package com.feng.dsagent.chat.dto;

import com.feng.dsagent.chat.domain.ChatAttachment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TurnDTO(
    @NotBlank @Size(max = 16) String role,
    @NotBlank @Size(max = 4000) String content,
    @Size(max = 6) List<ChatAttachment> attachments
) {}
