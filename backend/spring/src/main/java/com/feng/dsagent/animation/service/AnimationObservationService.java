package com.feng.dsagent.animation.service;

import com.feng.dsagent.animation.entity.AnimationRecordEntity;
import com.feng.dsagent.animation.error.AnimationErrorCode;
import com.feng.dsagent.animation.mapper.AnimationMapper;
import com.feng.dsagent.animation.vo.AnimationObservationVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.learning.dto.LearningEventCommandDTO;
import com.feng.dsagent.learning.service.LearningEventService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnimationObservationService {

    private final AnimationMapper mapper;
    private final LearningEventService learningEvents;

    public AnimationObservationService(AnimationMapper mapper, LearningEventService learningEvents) {
        this.mapper = mapper;
        this.learningEvents = learningEvents;
    }

    @Transactional
    public AnimationObservationVO record(long userId, String recordId, String observation) {
        AnimationRecordEntity animation = Optional.ofNullable(mapper.findOwned(userId, normalizeRecordId(recordId)))
            .orElseThrow(this::notFound);
        String normalizedObservation = normalizeObservation(observation);
        mapper.insertObservation(
            animation.id(),
            userId,
            normalizedObservation,
            "USER",
            "api/v1/animations/" + animation.id() + "/observations",
            "1.0"
        );
        mapper.updateRecordObservation(animation.id(), userId, normalizedObservation);
        tools.jackson.databind.node.ObjectNode payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put("observation", normalizedObservation);
        learningEvents.record(userId, new LearningEventCommandDTO(
            "ANIMATION_OBSERVATION",
            animation.chapterId(),
            animation.id(),
            payload
        ));
        return new AnimationObservationVO(animation.id(), normalizedObservation);
    }

    private String normalizeRecordId(String recordId) {
        if (recordId == null || recordId.isBlank() || recordId.length() > 64) {
            throw notFound();
        }
        return recordId.trim();
    }

    private String normalizeObservation(String observation) {
        String normalized = observation == null ? "" : observation.trim();
        if (normalized.isBlank()) {
            throw AnimationErrorCode.OBSERVATION_REQUIRED.toException();
        }
        if (normalized.length() > 2_000) {
            throw AnimationErrorCode.OBSERVATION_TOO_LONG.toException();
        }
        return normalized;
    }

    private BizException notFound() {
        return AnimationErrorCode.RECORD_NOT_FOUND.toException();
    }
}
