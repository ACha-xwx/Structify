package com.feng.dsagent.learning.vo;

import com.feng.dsagent.animation.domain.AnimationDefinition;
import java.time.Instant;

/** A renderer-compatible DSVP scene that belongs to the authenticated learner. */
public record PersistedWorkbenchSceneVO(
    String recordId,
    String chapterId,
    AnimationDefinition definition,
    Instant updatedAt
) {
}
