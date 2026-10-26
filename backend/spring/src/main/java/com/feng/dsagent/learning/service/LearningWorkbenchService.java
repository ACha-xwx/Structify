package com.feng.dsagent.learning.service;

import com.feng.dsagent.animation.domain.AnimationDefinition;
import com.feng.dsagent.learning.mapper.LearningWorkbenchMapper;
import com.feng.dsagent.learning.mapper.LearningWorkbenchMapper.PersistedSceneCandidate;
import com.feng.dsagent.learning.vo.LearningProgressVO;
import com.feng.dsagent.learning.vo.LearningWorkbenchVO;
import com.feng.dsagent.learning.vo.PersistedWorkbenchSceneVO;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningWorkbenchService {

    private static final int CANDIDATE_LIMIT = 8;

    private final LearningProgressService progress;
    private final LearningWorkbenchMapper mapper;
    private final ObjectMapper objectMapper;

    public LearningWorkbenchService(
        LearningProgressService progress,
        LearningWorkbenchMapper mapper,
        ObjectMapper objectMapper
    ) {
        this.progress = progress;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public LearningWorkbenchVO workbench(long userId) {
        LearningProgressVO learningProgress = progress.progress(userId);
        PersistedWorkbenchSceneVO scene = mapper.findLatestOwnedDsvpScenes(userId, CANDIDATE_LIMIT).stream()
            .map(this::restore)
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(null);
        return new LearningWorkbenchVO(
            scene == null ? null : scene.chapterId(),
            learningProgress,
            scene
        );
    }

    private Optional<PersistedWorkbenchSceneVO> restore(PersistedSceneCandidate candidate) {
        if (candidate.payloadJson() == null || candidate.payloadJson().isBlank()) {
            return Optional.empty();
        }
        try {
            AnimationDefinition definition = objectMapper.readValue(candidate.payloadJson(), AnimationDefinition.class);
            if (!isRenderable(definition)) {
                return Optional.empty();
            }
            return Optional.of(new PersistedWorkbenchSceneVO(
                candidate.recordId(),
                candidate.chapterId(),
                definition,
                candidate.updatedAt()
            ));
        } catch (Exception ignored) {
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
