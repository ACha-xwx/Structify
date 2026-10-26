package com.feng.dsagent.studyroom.fragment.domain;

import java.util.List;

public record FragmentExecutionContext(String runId, String fragmentId, String jobId,
        int attempt, String objective, String evidenceRevision, String resourceRevision,
        String promptVersion, String schemaVersion, FragmentBudget budget,
        List<String> completionCriteria) {
    public FragmentExecutionContext {
        completionCriteria = List.copyOf(completionCriteria);
    }
}
