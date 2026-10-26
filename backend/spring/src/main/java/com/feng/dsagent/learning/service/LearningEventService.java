package com.feng.dsagent.learning.service;

import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.learning.converter.LearningEventConverter;
import com.feng.dsagent.learning.dto.LearningEventCommandDTO;
import com.feng.dsagent.learning.error.LearningErrorCode;
import com.feng.dsagent.learning.mapper.LearningEventMapper;
import com.feng.dsagent.learning.mapper.LearningEventMapper.LearningEventInsert;
import com.feng.dsagent.learning.vo.LearningEventVO;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningEventService {

    private static final Set<String> EVENT_TYPES = Set.of(
        "RESOURCE_VIEW",
        "RESOURCE_DOWNLOAD",
        "ANIMATION_SIMULATION",
        "ANIMATION_OBSERVATION",
        "CLASSROOM_ANSWER",
        "CLASSROOM_SKIP",
        "CODE_REVIEW",
        "REVIEW_COMPLETED",
        "WEAKNESS_RECORDED"
    );
    private static final Set<String> SERVER_MANAGED_EVENT_TYPES = Set.of(
        "ANIMATION_OBSERVATION",
        "ANIMATION_SIMULATION",
        "CLASSROOM_ANSWER",
        "CLASSROOM_SKIP",
        "CODE_REVIEW"
    );
    private static final int MAX_PAYLOAD_LENGTH = 8_000;

    private final LearningEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LearningEventConverter converter;

    public LearningEventService(LearningEventMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, Clock.systemUTC(), Mappers.getMapper(LearningEventConverter.class));
    }

    public LearningEventService(LearningEventMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this(mapper, objectMapper, clock, Mappers.getMapper(LearningEventConverter.class));
    }

    @Autowired
    public LearningEventService(
        LearningEventMapper mapper,
        ObjectMapper objectMapper,
        Clock clock,
        LearningEventConverter converter
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.converter = converter;
    }

    public LearningEventVO record(long userId, LearningEventCommandDTO command) {
        if (command == null) {
            throw invalidEvent();
        }
        String eventType = normalizeEventType(command.eventType());
        String chapterId = normalizeChapterId(command.chapterId());
        String referenceId = normalizeReferenceId(command.referenceId());
        String payloadJson = serialize(command.payload());
        Instant createdAt = clock.instant();
        LearningEventInsert row = new LearningEventInsert();
        row.setUserId(userId);
        row.setChapterId(chapterId);
        row.setEventType(eventType);
        row.setReferenceId(referenceId);
        row.setPayloadJson(payloadJson);
        row.setCreatedAt(createdAt);
        mapper.insertEvent(row);
        if (row.getId() == null) {
            throw new IllegalStateException("Learning event did not return a generated id");
        }
        return converter.toView(row);
    }

    public LearningEventVO recordUserSubmitted(long userId, LearningEventCommandDTO command) {
        if (command == null) {
            throw invalidEvent();
        }
        String eventType = normalizeEventType(command.eventType());
        if (SERVER_MANAGED_EVENT_TYPES.contains(eventType)) {
            throw LearningErrorCode.EVENT_SERVER_MANAGED.toException();
        }
        return record(userId, new LearningEventCommandDTO(
            eventType,
            command.chapterId(),
            command.referenceId(),
            command.payload()
        ));
    }

    private String normalizeEventType(String eventType) {
        String normalized = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(normalized)) {
            throw invalidEvent();
        }
        return normalized;
    }

    private String normalizeChapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        String normalized = chapterId.trim();
        if (normalized.length() > 64 || !normalized.matches("^[0-9]{2}-[a-z0-9-]+$")
                || mapper.countPublishedChapter(normalized) <= 0) {
            throw LearningErrorCode.CHAPTER_INVALID.toException();
        }
        return normalized;
    }

    private String normalizeReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return null;
        }
        String normalized = referenceId.trim();
        if (normalized.length() > 96) {
            throw LearningErrorCode.REFERENCE_INVALID.toException();
        }
        return normalized;
    }

    private String serialize(tools.jackson.databind.JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.length() > MAX_PAYLOAD_LENGTH) {
                throw LearningErrorCode.PAYLOAD_TOO_LONG.toException();
            }
            return json;
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw LearningErrorCode.PAYLOAD_INVALID.toException();
        }
    }

    private BizException invalidEvent() {
        return LearningErrorCode.EVENT_TYPE_INVALID.toException();
    }
}
