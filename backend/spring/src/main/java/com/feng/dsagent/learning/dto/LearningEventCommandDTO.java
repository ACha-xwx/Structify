package com.feng.dsagent.learning.dto;

import tools.jackson.databind.JsonNode;

public record LearningEventCommandDTO(
    String eventType,
    String chapterId,
    String referenceId,
    JsonNode payload
) {
}
