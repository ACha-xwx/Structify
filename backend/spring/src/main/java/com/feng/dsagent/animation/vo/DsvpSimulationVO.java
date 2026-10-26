package com.feng.dsagent.animation.vo;

import tools.jackson.databind.JsonNode;

import com.feng.dsagent.animation.domain.AnimationDefinition;
public record DsvpSimulationVO(
    String protocol,
    JsonNode request,
    JsonNode trace,
    AnimationDefinition animationData,
    String recordId,
    boolean evidencePersisted,
    String animationRecordId,
    String resolvedChapterId,
    String matchSource
) {

    /** Keeps the adapter-only response compatible with callers that do not persist evidence. */
    public DsvpSimulationVO(
        String protocol,
        JsonNode request,
        JsonNode trace,
        AnimationDefinition animationData,
        String recordId
    ) {
        this(
            protocol,
            request,
            trace,
            animationData,
            recordId,
            recordId != null,
            recordId,
            null,
            recordId == null ? "NONE" : "UNRESOLVED"
        );
    }
}
