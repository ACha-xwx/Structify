package com.feng.dsagent.learning;

import java.time.Instant;
import java.util.List;

public interface LearningWorkbenchRepository {

    public List<PersistedSceneCandidate> findLatestOwnedDsvpScenes(long userId);

    public record PersistedSceneCandidate(
        String recordId,
        String chapterId,
        String payloadJson,
        Instant updatedAt
    ) {
    }
}
