package com.feng.dsagent.studyroom;

import java.util.List;

final class StudyroomSelectionDiagnostics {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StudyroomSelectionDiagnostics.class);
    static void record(String requestId, StudyroomModelOutput output, List<StudyroomResourceCatalog.Candidate> candidates) {
        for (var step : output.steps()) {
            for (var type : StudyroomModelOutput.ResourceType.values()) {
                var selected = step.resources().stream().filter(r -> r.type() == type).map(StudyroomModelOutput.ResourceRef::id).toList();
                long count = candidates.stream().filter(c -> c.type() == type).count();
                LOG.info("Studyroom resource decision: request={} step={} type={} candidates={} selected={} reason={}",
                    requestId, step.id(), type.value(), count, selected, selected.isEmpty() ? (count == 0 ? "NO_MATCH" : "MODEL_NOT_SELECTED") : "SELECTED");
            }
        }
    }
}
