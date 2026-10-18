package com.feng.dsagent.studyroom;

import java.time.Instant;
import java.util.List;

public record StudyroomView(
    String id,
    String chapterId,
    String title,
    String initialQuestion,
    String summary,
    String currentStepId,
    int version,
    Instant createdAt,
    Instant updatedAt,
    List<Step> steps,
    List<Message> messages
) {
    public StudyroomView {
        steps = steps == null ? List.of() : List.copyOf(steps);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public record Step(
        String id,
        String stepKey,
        String type,
        String title,
        String explanation,
        List<String> evidenceIds,
        List<Resource> resources,
        int order,
        boolean supplemental,
        String parentStepId,
        Instant createdAt,
        StudyroomScene.Orchestration orchestration
    ) {
        public Step {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }

    public record Resource(String type, String id, String title, String description, Object parameters, boolean available) {
    }

    public record Message(String id, String role, String content, List<String> sourceIds, String stepId, Instant createdAt) {
        public Message {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record Progress(String currentStepId, int version, Instant updatedAt) {
    }
}
