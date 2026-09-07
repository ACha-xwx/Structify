package com.feng.dsagent.learning;

import com.feng.dsagent.animation.AnimationDefinition;
import java.time.Instant;

/** A renderer-compatible DSVP scene that belongs to the authenticated learner. */
public record PersistedWorkbenchSceneView(
    String recordId,
    String chapterId,
    AnimationDefinition definition,
    Instant updatedAt
) {
}
