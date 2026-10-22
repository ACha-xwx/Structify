package com.feng.dsagent.studyroom.events;

import java.util.List;
import java.util.Map;

public record ClassroomEvent(String streamId, String operationId, int attempt, String runId,
        Integer revision, long sequence, long stateVersion, String kind, Map<String,Object> payload) {
    public record Snapshot(String streamId, String operationId, int attempt, String runId,
            Integer revision, long cursor, long floor, boolean terminal, long stateVersion, Map<String,Object> state) {}
    public record Batch(Snapshot snapshot, List<ClassroomEvent> events, boolean reset) {}
}
