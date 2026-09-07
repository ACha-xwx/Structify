package com.feng.dsagent.learning;

/**
 * Authenticated learning-workbench read model.
 *
 * <p>{@code currentChapterId} is set only by a restorable saved scene. Aggregate
 * progress remains descriptive and must not be interpreted as an explicit task
 * selection.</p>
 */
public record LearningWorkbenchView(
    String currentChapterId,
    LearningProgressView progress,
    PersistedWorkbenchSceneView scene
) {
}
