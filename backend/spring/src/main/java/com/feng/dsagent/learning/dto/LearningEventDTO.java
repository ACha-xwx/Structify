package com.feng.dsagent.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record LearningEventDTO(
    @NotBlank @Size(max = 48) String eventType,
    @Size(max = 64) String chapterId,
    @Size(max = 96) String referenceId,
    JsonNode payload
) {}
