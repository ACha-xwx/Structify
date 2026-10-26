package com.feng.dsagent.studyroom.domain;

import java.util.List;
import java.util.Set;

public record StudyroomScene(String id, String kind, String purpose, List<Item> items,
    String codeMode, String task, String stdin, String expectedObservation) {
    public StudyroomScene {
        if (id == null || id.isBlank() || purpose == null || purpose.isBlank()
            || !Set.of("TEXT", "PPT", "ANIMATION", "C_CODE").contains(kind)) throw new IllegalArgumentException("Invalid scene");
        items = items == null ? List.of() : List.copyOf(items);
        if (kind.equals("TEXT") && !items.isEmpty()) throw new IllegalArgumentException("Text scene cannot bind resources");
        if (!kind.equals("TEXT") && items.isEmpty()) throw new IllegalArgumentException("Resource scene needs items");
        if (kind.equals("C_CODE") && !Set.of("READ", "EXPERIMENT").contains(codeMode == null ? "" : codeMode))
            throw new IllegalArgumentException("Invalid code mode");
        if ("EXPERIMENT".equals(codeMode) && (task == null || task.isBlank() || expectedObservation == null || expectedObservation.isBlank()))
            throw new IllegalArgumentException("Experiment requires task and expected observation");
    }
    public record Item(String resourceId, String explanation, Integer startLine, Integer endLine) {
        public Item {
            if (resourceId == null || resourceId.isBlank() || explanation == null || explanation.length() > 4000)
                throw new IllegalArgumentException("Invalid scene item");
            if ((startLine == null) != (endLine == null) || (startLine != null && (startLine < 1 || endLine < startLine)))
                throw new IllegalArgumentException("Invalid code line range");
        }
    }
    public record Orchestration(int schemaVersion, String objective, String intent, StudyroomScene primary, List<StudyroomScene> supplementary) {
        public Orchestration {
            if (schemaVersion != 2 || objective == null || objective.isBlank() || objective.length() > 400
                || primary == null || !Set.of("CONCEPT", "PROCESS", "IMPLEMENTATION", "EXPERIMENT", "CHECK").contains(intent))
                throw new IllegalArgumentException("Invalid orchestration");
            supplementary = supplementary == null ? List.of() : List.copyOf(supplementary);
        }
        public List<StudyroomScene> scenes() {
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(primary), supplementary.stream()).toList();
        }
    }
}
