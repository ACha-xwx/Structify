package com.feng.dsagent.animation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * One playable step. The flat fields drive the simple linear renderers; the optional DSVP fields carry the
 * rich, engine-computed snapshot for structures a row of values cannot express — a tree's nodes and edges,
 * a graph's distances, the highlights inside a sort pass.
 *
 * <p>They are always produced by the local simulator, never by the model.
 */
public record AnimationStep(
    String op,
    String label,
    String note,
    Object value,
    Integer index,
    Integer node,
    Integer i,
    Integer j,
    String key,
    String val,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Object> state,
    @JsonInclude(JsonInclude.Include.NON_NULL) String phase,
    @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode dsvpState,
    @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode dsvpHighlights,
    @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode dsvpActions
) {

    public AnimationStep {
        label = label == null || label.isBlank() ? note : label;
        state = state == null ? null : Collections.unmodifiableList(new ArrayList<>(state));
    }

    public AnimationStep(
        String op,
        String label,
        String note,
        Object value,
        Integer index,
        Integer node,
        Integer i,
        Integer j,
        String key,
        String val,
        List<Object> state
    ) {
        this(op, label, note, value, index, node, i, j, key, val, state, null, null, null, null);
    }

    public AnimationStep(String op, String label, String note, Object value, Integer index, Integer node, Integer i, Integer j, String key, String val) {
        this(op, label, note, value, index, node, i, j, key, val, null, null, null, null, null);
    }

    public AnimationStep(String op, String note, Object value) {
        this(op, note, note, value, null, null, null, null, null, null, null, null, null, null, null);
    }
}
