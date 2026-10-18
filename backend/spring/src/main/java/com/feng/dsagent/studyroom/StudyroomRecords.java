package com.feng.dsagent.studyroom;

import java.time.Instant;
import java.util.List;

public final class StudyroomRecords {

    private StudyroomRecords() {
    }

    public record Room(
        String id,
        long userId,
        String chapterId,
        String title,
        String initialQuestion,
        String summary,
        String currentStepId,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record Step(
        String id,
        String stepKey,
        String type,
        String title,
        String explanation,
        String evidenceJson,
        String resourcesJson,
        int stepOrder,
        boolean supplemental,
        String parentStepId,
        Instant createdAt,
        String orchestrationJson
    ) {
        public Step(String id, String stepKey, String type, String title, String explanation, String evidenceJson,
            String resourcesJson, int stepOrder, boolean supplemental, String parentStepId, Instant createdAt) {
            this(id, stepKey, type, title, explanation, evidenceJson, resourcesJson, stepOrder, supplemental, parentStepId, createdAt, null);
        }
    }

    public record Message(
        String id,
        String role,
        String content,
        String sourcesJson,
        String stepId,
        Instant createdAt
    ) {
    }

    public record Aggregate(Room room, List<Step> steps, List<Message> messages) {
        public Aggregate {
            steps = steps == null ? List.of() : List.copyOf(steps);
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }
}
