package com.feng.dsagent.chat.dto;

import jakarta.validation.constraints.Size;

public record ChatSessionUpdateDTO(
    @Size(max = 200) String title,
    Boolean pinned
) {}
