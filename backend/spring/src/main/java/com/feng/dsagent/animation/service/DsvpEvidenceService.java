package com.feng.dsagent.animation.service;

import com.feng.dsagent.animation.DsvpEvidenceResolution;
import com.feng.dsagent.animation.DsvpSimulationResponse;
import com.feng.dsagent.animation.DsvpAnimationAdapter;
import com.feng.dsagent.animation.DsvpEvidenceResolver;
import com.feng.dsagent.animation.AnimationRepository;
import com.feng.dsagent.animation.DsvpEvidenceSource;

import com.feng.dsagent.animation.mapper.DsvpEvidenceMapper;
import com.feng.dsagent.learning.LearningEventCommand;
import com.feng.dsagent.learning.service.LearningEventService;
import com.feng.dsagent.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Persists server-owned DSVP evidence after a request has passed validation. */
@Service
public class DsvpEvidenceService {

    public static final String SIMULATION_EVENT_TYPE = "ANIMATION_SIMULATION";
    private static final String REVIEW_STATUS = "UNREVIEWED";

    private final DsvpAnimationAdapter adapter;
    private final AnimationRepository animations;
    private final DsvpEvidenceMapper evidence;
    private final LearningEventService learningEvents;
    private final ObjectMapper objectMapper;
    private final DsvpEvidenceResolver resolver;

    public DsvpEvidenceService(
        DsvpAnimationAdapter adapter,
        AnimationRepository animations,
        DsvpEvidenceMapper evidence,
        LearningEventService learningEvents,
        ObjectMapper objectMapper,
        DsvpEvidenceResolver resolver
    ) {
        this.adapter = adapter;
        this.animations = animations;
        this.evidence = evidence;
        this.learningEvents = learningEvents;
        this.objectMapper = objectMapper;
        this.resolver = resolver;
    }

    /**
     * Adapts and records a DSVP request as one atomic server-owned evidence unit.
     * The source enum keeps the channel classification outside the client payload.
     */
    @Transactional
    public DsvpSimulationResponse simulate(long userId, JsonNode request, DsvpEvidenceSource source) {
        return simulate(userId, Set.of("STUDENT"), request, source);
    }

    @Transactional
    public DsvpSimulationResponse simulate(
        long userId,
        Set<String> roles,
        JsonNode request,
        DsvpEvidenceSource source
    ) {
        DsvpSimulationResponse response = adapter.adapt(request);
        DsvpEvidenceResolution resolution = resolver.resolve(
            userId,
            roles == null ? Set.of("STUDENT") : Set.copyOf(roles),
            response.request(),
            source
        );
        if (!resolution.persistable()) {
            return preview(response);
        }
        lockEvidenceOwner(userId);
        String requestJson = response.request().toString();
        String traceId = evidenceTraceId(userId, requestJson);
        response = withTraceId(response, traceId);
        String requestHash = sha256(requestJson);
        String sourceRef = resolution.sourceRef();

        Optional<String> existing = existingRecord(
            userId,
            traceId,
            requestHash,
            source,
            sourceRef,
            resolution.chapterId()
        );
        if (existing.isPresent()) {
            return persisted(response, existing.get(), resolution);
        }

        String recordId = persistAnimationRecord(userId, resolution.chapterId(), response);

        persistSnapshot(
            traceId,
            recordId,
            response.protocol(),
            requestJson,
            requestHash,
            source.type(),
            sourceRef,
            response.request().path("version").asText("1.0")
        );
        learningEvents.record(userId, new LearningEventCommand(
            SIMULATION_EVENT_TYPE,
            resolution.chapterId(),
            traceId,
            learningPayload(
                response,
                traceId,
                recordId,
                requestHash,
                source,
                sourceRef,
                resolution.chapterId(),
                resolution.matchSource()
            )
        ));
        return persisted(response, recordId, resolution);
    }

    private String persistAnimationRecord(
        long userId,
        String chapterId,
        DsvpSimulationResponse response
    ) {
        try {
            return animations.save(userId, chapterId, response.animationData(), objectMapper.writeValueAsString(response.animationData()));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to persist DSVP animation record", error);
        }
    }

    private void lockEvidenceOwner(long userId) {
        evidence.lockUser(userId);
    }

    private synchronized void persistSnapshot(
        String traceId,
        String animationRecordId,
        String protocol,
        String requestJson,
        String requestHash,
        String sourceType,
        String sourceRef,
        String versionLabel
    ) {
        String existingHash = evidence.selectRequestHash(traceId).stream().findFirst().orElse(null);
        if (existingHash != null) {
            if (!existingHash.equals(requestHash)) {
                throw new IllegalStateException("DSVP trace id collision for " + traceId);
            }
            return;
        }
        evidence.insertSnapshot(
            traceId,
            animationRecordId,
            protocol,
            requestJson,
            requestHash,
            sourceType,
            sourceRef,
            versionLabel,
            REVIEW_STATUS
        );
    }

    private ObjectNode learningPayload(
        DsvpSimulationResponse response,
        String traceId,
        String recordId,
        String requestHash,
        DsvpEvidenceSource source,
        String sourceRef,
        String chapterId,
        String matchSource
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("completed", true);
        payload.put("trace_id", traceId);
        payload.put("record_id", recordId);
        payload.put("request_hash", requestHash);
        payload.put("protocol", response.protocol());
        payload.put("source_type", source.type());
        payload.put("source_ref", sourceRef);
        payload.put("chapter_id", chapterId);
        payload.put("match_source", matchSource);
        payload.put("version_label", response.request().path("version").asText("1.0"));
        payload.put("structure", response.request().path("structure").asText());
        payload.put("operation", response.request().path("operation").asText());
        payload.put("step_count", response.animationData().steps().size());
        return payload;
    }

    private Optional<String> existingRecord(
        long userId,
        String traceId,
        String requestHash,
        DsvpEvidenceSource source,
        String sourceRef,
        String chapterId
    ) {
        var rows = evidence.selectExistingSnapshot(traceId);
        if (rows.isEmpty()) return Optional.empty();
        DsvpEvidenceMapper.ExistingSnapshotRow existing = rows.get(0);
        if (!requestHash.equals(existing.requestHash())
            || !source.type().equals(existing.sourceType())
            || !java.util.Objects.equals(sourceRef, existing.sourceRef())
            || !chapterId.equals(existing.chapterId())
            || existing.userId() == null
            || existing.userId() != userId
            || existing.animationRecordId() == null) {
            throw new ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "DSVP_TRACE_COLLISION",
                "DSVP trace id is already bound to different evidence"
            );
        }
        return Optional.of(existing.animationRecordId());
    }

    private DsvpSimulationResponse persisted(
        DsvpSimulationResponse response,
        String recordId,
        DsvpEvidenceResolution resolution
    ) {
        return new DsvpSimulationResponse(
            response.protocol(),
            response.request(),
            response.trace(),
            response.animationData(),
            recordId,
            true,
            recordId,
            resolution.chapterId(),
            resolution.matchSource()
        );
    }

    private DsvpSimulationResponse preview(DsvpSimulationResponse response) {
        return new DsvpSimulationResponse(
            response.protocol(),
            response.request(),
            response.trace(),
            response.animationData(),
            null,
            false,
            null,
            null,
            "NONE"
        );
    }

    private DsvpSimulationResponse withTraceId(DsvpSimulationResponse response, String traceId) {
        ObjectNode trace = (ObjectNode) response.trace().deepCopy();
        trace.put("trace_id", traceId);
        return new DsvpSimulationResponse(
            response.protocol(),
            response.request(),
            trace,
            response.animationData(),
            null
        );
    }

    private String evidenceTraceId(long userId, String requestJson) {
        return "dsvp_" + sha256(userId + ":" + requestJson).substring(0, 20);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash DSVP request", error);
        }
    }
}
