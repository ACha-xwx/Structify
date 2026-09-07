package com.feng.dsagent.learning;

import com.feng.dsagent.animation.AnimationDefinition;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningWorkbenchService {

    private final LearningProgressService progress;
    private final LearningWorkbenchRepository repository;
    private final ObjectMapper objectMapper;

    LearningWorkbenchService(
        LearningProgressService progress,
        LearningWorkbenchRepository repository,
        ObjectMapper objectMapper
    ) {
        this.progress = progress;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public LearningWorkbenchView workbench(long userId) {
        LearningProgressView learningProgress = progress.progress(userId);
        PersistedWorkbenchSceneView scene = repository.findLatestOwnedDsvpScenes(userId).stream()
            .map(this::restore)
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(null);
        return new LearningWorkbenchView(
            scene == null ? null : scene.chapterId(),
            learningProgress,
            scene
        );
    }

    private Optional<PersistedWorkbenchSceneView> restore(
        LearningWorkbenchRepository.PersistedSceneCandidate candidate
    ) {
        if (candidate.payloadJson() == null || candidate.payloadJson().isBlank()) {
            return Optional.empty();
        }
        try {
            AnimationDefinition definition = objectMapper.readValue(candidate.payloadJson(), AnimationDefinition.class);
            if (!isRenderable(definition)) {
                return Optional.empty();
            }
            return Optional.of(new PersistedWorkbenchSceneView(
                candidate.recordId(),
                candidate.chapterId(),
                definition,
                candidate.updatedAt()
            ));
        } catch (Exception ignored) {
            // Historical payloads are not trusted as a workbench scene unless they render safely.
            return Optional.empty();
        }
    }

    private boolean isRenderable(AnimationDefinition definition) {
        return definition != null
            && Boolean.TRUE.equals(definition.animation())
            && present(definition.type())
            && present(definition.title())
            && !definition.steps().isEmpty()
            && definition.steps().stream().allMatch(step -> step != null && present(step.op()));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
