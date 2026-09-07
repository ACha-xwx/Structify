package com.feng.dsagent.learning;

import java.time.Instant;
import java.util.List;

interface LearningWorkbenchRepository {

    List<PersistedSceneCandidate> findLatestOwnedDsvpScenes(long userId);

    record PersistedSceneCandidate(
        String recordId,
        String chapterId,
        String payloadJson,
        Instant updatedAt
    ) {
    }
}
