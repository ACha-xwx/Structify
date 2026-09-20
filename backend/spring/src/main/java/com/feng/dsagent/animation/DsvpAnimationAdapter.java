package com.feng.dsagent.animation;

import com.feng.dsagent.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public final class DsvpAnimationAdapter {

    static final String VERSION = "1.0";
    static final int MAXIMUM_REQUEST_BYTES = 256 * 1024;
    static final int MAXIMUM_SOURCE_REF_LENGTH = 160;

    private static final Set<String> REQUEST_FIELDS = Set.of(
        "version", "structure", "operation", "params", "initial_state", "options", "source_ref",
        "context", "chapter_id", "chapterId", "lesson_id", "lessonId", "presentation_id",
        "presentationId", "presentation_page_id", "presentationPageId", "classroom_session_id",
        "classroomSessionId"
    );
    private static final Set<String> CONTEXT_FIELDS = Set.of(
        "chapter_id", "lesson_id", "presentation_id", "presentation_page_id", "classroom_session_id",
        "source_type", "source_ref"
    );
    private static final Set<String> PARAM_FIELDS = Set.of(
        "value", "capacity", "position", "index", "node", "i", "j", "key", "val", "order", "edges", "directed"
    );
    private static final Map<String, Set<String>> OPERATIONS = Map.of(
        "stack", Set.of("push", "pop", "peek"),
        "queue", Set.of("enqueue", "dequeue", "peek"),
        "sequential_list", Set.of("insert", "delete", "merge"),
        "linked_list", Set.of("append", "insert", "delete", "find"),
        "tree", Set.of("visit", "highlight", "traverse"),
        "graph", Set.of("bfs", "dfs", "visit", "highlight"),
        "heap", Set.of("insert", "extract", "peek"),
        "hash", Set.of("put", "get", "delete"),
        "array", Set.of("set", "insert", "delete", "swap", "get")
    );
    private static final Map<String, String> RENDERER_TYPES = Map.of(
        "sequential_list", "array",
        "linked_list", "list",
        "graph", "tree"
    );

    private final ObjectMapper objectMapper;
    private final AnimationValidator validator;
    private final DsvpLocalEngine engine;

    /** Adapter-only construction (tests, offline callers): the local engine is simply absent. */
    public DsvpAnimationAdapter(ObjectMapper objectMapper, AnimationValidator validator) {
        this(objectMapper, validator, null);
    }

    @Autowired
    public DsvpAnimationAdapter(ObjectMapper objectMapper, AnimationValidator validator, DsvpLocalEngine engine) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.engine = engine;
    }

    /**
     * Validation shared by both execution paths, then the local engine, then the in-process simulator.
     *
     * <p>The engine covers the whole reviewed textbook (167 capabilities) and computes every frame itself.
     * It is asked first; anything it does not serve — the generic nine-structure surface frozen in
     * {@code contracts/dsvp.schema.json}, and every animation in an environment without the engine — still
     * runs through {@link DsvpSimulator}.
     */
    public DsvpSimulationResponse adapt(JsonNode input) {
        requireObject(input, "request");
        if (input.toString().getBytes(StandardCharsets.UTF_8).length > MAXIMUM_REQUEST_BYTES) {
            throw invalid("DSVP_REQUEST_TOO_LARGE", "DSVP request is too large");
        }
        rejectUnknown(input, REQUEST_FIELDS, "request");
        validateSourceRef(input);
        validateContext(input);
        String version = text(input, "version", 16);
        if (!VERSION.equals(version)) {
            throw invalid("DSVP_VERSION_UNSUPPORTED", "Unsupported DSVP version");
        }

        DsvpSimulationResponse local = adaptWithLocalEngine(input);
        if (local != null) return local;

        String structure = text(input, "structure", 32);
        String operation = text(input, "operation", 32);
        if (!OPERATIONS.getOrDefault(structure, Set.of()).contains(operation)) {
            throw invalid("DSVP_OPERATION_UNSUPPORTED", "Unsupported DSVP structure or operation");
        }

        JsonNode params = input.path("params");
        requireObject(params, "params");
        rejectUnknown(params, PARAM_FIELDS, "params");
        JsonNode initialState = input.path("initial_state");
        requireObject(initialState, "initial_state");
        JsonNode data = initialState.path("data");
        if (!data.isArray()) {
            throw invalid("DSVP_INITIAL_STATE_INVALID", "initial_state.data must be an array");
        }
        if (data.size() > AnimationValidator.MAX_INITIAL_ITEMS) {
            throw invalid("DSVP_INITIAL_STATE_TOO_LARGE", "initial_state.data is too large");
        }

        int capacity = integer(params.path("capacity"), Math.max(10, data.size() + 1), 1, 100, "capacity");
        if (!"sequential_list".equals(structure) || !"merge".equals(operation)) {
            if (data.size() > capacity) {
                throw invalid("DSVP_INITIAL_STATE_OVERFLOW", "initial_state exceeds capacity");
            }
        }

        DsvpSimulator.Result simulation = DsvpSimulator.run(structure, operation, input, capacity, objectMapper);
        List<Object> initial = simulation.initial();
        List<AnimationStep> steps = annotateWithFrames(structure, simulation, objectMapper);
        AnimationDefinition definition = new AnimationDefinition(
            true,
            RENDERER_TYPES.getOrDefault(structure, structure),
            abbreviate(structure + " " + operation, AnimationValidator.MAX_TITLE_LENGTH),
            abbreviate("DSVP " + VERSION + " " + structure + "/" + operation, AnimationValidator.MAX_DESCRIPTION_LENGTH),
            initial,
            steps
        );
        // This definition is emitted by deterministic execution, not untrusted model JSON.
        // Cross-structure primitive steps (e.g. heap swaps) are represented by snapshots.
        ObjectNode normalizedRequest = normalizeRequest(input, capacity);
        ObjectNode executedTrace = trace(normalizedRequest, structure, operation, definition);
        executedTrace.set("initial_state", objectMapper.valueToTree(initial));
        executedTrace.set("edges", objectMapper.valueToTree(simulation.edges()));
        executedTrace.set("final_state", objectMapper.valueToTree(simulation.states().getLast()));
        executedTrace.set("visited", objectMapper.valueToTree(simulation.visited()));
        for (int i = 0; i < steps.size(); i++) {
            ObjectNode traceStep = (ObjectNode) executedTrace.path("steps").get(i);
            traceStep.set("state", objectMapper.valueToTree(simulation.states().get(i)));
            if (steps.get(i).index() != null) traceStep.put("active_index", steps.get(i).index());
        }
        return new DsvpSimulationResponse(
            "dsvp/1.0",
            normalizedRequest,
            executedTrace,
            definition,
            null
        );
    }


    /**
     * The in-process simulator only computes flat value snapshots, but the shared stage renders from the
     * structured {@code dsvpState} view panels the local engine emits. Build those panels here so a
     * fallback animation draws a real tree/graph/heap or an array row instead of "nothing to render".
     *
     * <p>Node ids are the initial indices (tree holes skipped for the node list, edges keep indices), so
     * the renderer's active/visited matching works without extra mapping.
     */
    private List<AnimationStep> annotateWithFrames(String structure, DsvpSimulator.Result simulation, ObjectMapper mapper) {
        List<AnimationStep> steps = simulation.steps();
        List<Object> initial = simulation.initial();
        List<List<Integer>> edges = simulation.edges();
        List<Integer> visited = simulation.visited();
        boolean visitedAligns = !visited.isEmpty() && visited.size() == steps.size();
        boolean nodeLike = structure.equals("tree") || structure.equals("graph") || structure.equals("heap");
        List<AnimationStep> annotated = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            AnimationStep step = steps.get(i);
            ObjectNode frame = mapper.createObjectNode();
            ArrayNode view = frame.putArray("view");
            if (nodeLike) {
                ObjectNode panel = view.addObject();
                panel.put("role", structure.equals("graph") ? "graph" : "tree");
                panel.putArray("values");
                ArrayNode nodes = panel.putArray("nodes");
                for (int index = 0; index < initial.size(); index++) {
                    Object item = initial.get(index);
                    if (structure.equals("tree") && item == null) continue;
                    ObjectNode node = nodes.addObject();
                    node.put("id", index);
                    node.put("label", String.valueOf(item));
                }
                ArrayNode edgeList = panel.putArray("edges");
                for (List<Integer> edge : edges) {
                    ArrayNode pair = edgeList.addArray();
                    pair.add(edge.get(0));
                    pair.add(edge.get(1));
                }
                ObjectNode meta = view.addObject();
                meta.put("role", "meta");
                meta.putArray("values");
                if (step.index() != null) meta.put("current", step.index());
                if (visitedAligns) meta.set("visited", mapper.valueToTree(visited.subList(0, i + 1)));
                frame.put("kind", structure.equals("graph") ? "graph" : "tree");
            } else {
                ObjectNode panel = view.addObject();
                panel.put("role", "array");
                panel.set("values", mapper.valueToTree(simulation.states().get(i)));
                ObjectNode meta = view.addObject();
                meta.put("role", "meta");
                meta.putArray("values");
                if (step.index() != null) meta.put("index", step.index());
                frame.put("kind", "array");
            }
            annotated.add(new AnimationStep(step.op(), step.label(), step.note(), step.value(), step.index(),
                step.node(), step.i(), step.j(), step.key(), step.val(), step.state(), step.phase(), frame, null, null));
        }
        return annotated;
    }

    /**
     * Runs the request on the local deterministic engine. Null means "not served here" — the engine is
     * disabled, unreachable, or honestly reports {@code UNSUPPORTED_OPERATION} — and the caller falls back
     * to the in-process simulator. A genuine validation error is rethrown so the client learns why.
     */
    private DsvpSimulationResponse adaptWithLocalEngine(JsonNode input) {
        if (engine == null || !engine.enabled()) return null;
        ObjectNode forwarded = objectMapper.createObjectNode();
        forwarded.put("version", VERSION);
        forwarded.set("structure", input.path("structure"));
        forwarded.set("operation", input.path("operation"));
        forwarded.set("params", input.path("params").isObject() ? input.path("params") : objectMapper.createObjectNode());
        ObjectNode initialState = objectMapper.createObjectNode();
        JsonNode given = input.path("initial_state");
        initialState.set("data", given.path("data").isArray() ? given.path("data") : objectMapper.createArrayNode());
        if (given.path("metadata").isObject()) initialState.set("metadata", given.path("metadata"));
        forwarded.set("initial_state", initialState);
        String sourceRef = input.path("source_ref").asText("");
        if (!sourceRef.isBlank()) forwarded.put("source_ref", abbreviate(sourceRef, MAXIMUM_SOURCE_REF_LENGTH));

        Optional<JsonNode> reply;
        try {
            reply = engine.simulate(forwarded);
        } catch (ApiException error) {
            if ("UNSUPPORTED_OPERATION".equals(error.code())) return null;
            throw error;
        }
        if (reply.isEmpty()) return null;

        JsonNode payload = reply.get();
        JsonNode player = payload.path("player");
        if (!player.path("steps").isArray()) return null;
        return new DsvpSimulationResponse(
            "dsvp/1.0",
            payload.path("request").isObject() ? payload.path("request") : forwarded,
            payload.path("trace").isObject() ? payload.path("trace") : objectMapper.createObjectNode(),
            definitionFromLocalEngine(player),
            null
        );
    }

    /** Mirrors the engine's player payload into the shared animation contract, rich snapshots included. */
    private AnimationDefinition definitionFromLocalEngine(JsonNode player) {
        List<AnimationStep> steps = new ArrayList<>();
        for (JsonNode step : player.path("steps")) {
            String op = step.path("op").asText("inspect");
            String label = abbreviate(step.path("label").asText(""), AnimationValidator.MAX_LABEL_LENGTH);
            String note = abbreviate(step.path("note").asText(""), AnimationValidator.MAX_NOTE_LENGTH);
            steps.add(new AnimationStep(
                op.isBlank() ? "inspect" : op,
                label.isBlank() ? note : label,
                note,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                scalarList(step.path("stateSnapshot")),
                step.path("dsvpPhase").isTextual() && !step.path("dsvpPhase").asText().isBlank()
                    ? step.path("dsvpPhase").asText()
                    : null,
                snapshot(step.path("dsvpState")),
                snapshot(step.path("dsvpHighlights")),
                snapshot(step.path("dsvpActions"))
            ));
        }
        String structure = player.path("type").asText("array");
        return new AnimationDefinition(
            true,
            abbreviate(structure, AnimationValidator.MAX_TYPE_LENGTH),
            abbreviate(player.path("title").asText(structure), AnimationValidator.MAX_TITLE_LENGTH),
            abbreviate(player.path("description").asText(""), AnimationValidator.MAX_DESCRIPTION_LENGTH),
            scalarList(player.path("initial")),
            steps
        );
    }

    private JsonNode snapshot(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node;
    }

    private List<Object> scalarList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return convertArray(node);
    }

    private ObjectNode normalizeRequest(JsonNode input, int capacity) {
        ObjectNode normalized = (ObjectNode) input.deepCopy();
        ObjectNode params = normalized.withObject("params");
        if (!params.has("capacity")) params.put("capacity", capacity);
        ObjectNode initial = normalized.withObject("initial_state");
        ObjectNode metadata = initial.withObject("metadata");
        metadata.put("capacity", capacity);
        if (!normalized.has("options")) {
            ObjectNode options = normalized.putObject("options");
            options.put("language", "c");
            options.put("explain_level", "beginner");
        }
        if (!normalized.has("source_ref")) normalized.put("source_ref", "");
        normalizeContext(normalized);
        return normalized;
    }

    private void validateContext(JsonNode request) {
        if (request.has("context")) {
            JsonNode context = request.get("context");
            requireObject(context, "context");
            rejectUnknown(context, CONTEXT_FIELDS, "context");
            for (String field : CONTEXT_FIELDS) {
                if (context.has(field)) validateContextText(context, field);
            }
        }
        for (String field : Set.of(
            "chapter_id", "chapterId", "lesson_id", "lessonId", "presentation_id", "presentationId",
            "presentation_page_id", "presentationPageId", "classroom_session_id", "classroomSessionId"
        )) {
            if (request.has(field)) validateContextText(request, field);
        }
    }

    private void validateContextText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || value.asText().trim().length() > MAXIMUM_SOURCE_REF_LENGTH) {
            throw invalid("DSVP_CONTEXT_INVALID", field + " must be a bounded string");
        }
    }

    private void normalizeContext(ObjectNode request) {
        ObjectNode context;
        if (request.has("context")) {
            context = (ObjectNode) request.get("context");
        } else {
            context = objectMapper.createObjectNode();
        }
        copyAlias(request, context, "chapter_id", "chapterId");
        copyAlias(request, context, "lesson_id", "lessonId");
        copyAlias(request, context, "presentation_id", "presentationId");
        copyAlias(request, context, "presentation_page_id", "presentationPageId");
        copyAlias(request, context, "classroom_session_id", "classroomSessionId");
        if (request.has("source_ref") && !context.has("source_ref")) {
            context.set("source_ref", request.get("source_ref"));
        }
        if (context.isEmpty()) {
            request.remove("context");
        } else {
            request.set("context", context);
        }
        removeAliases(request);
    }

    private void copyAlias(ObjectNode request, ObjectNode context, String canonical, String alias) {
        if (!context.has(canonical) && request.has(alias)) context.set(canonical, request.get(alias));
        if (!context.has(canonical) && request.has(canonical)) context.set(canonical, request.get(canonical));
    }

    private void removeAliases(ObjectNode request) {
        for (String alias : Set.of(
            "chapter_id", "chapterId", "lesson_id", "lessonId", "presentation_id", "presentationId",
            "presentation_page_id", "presentationPageId", "classroom_session_id", "classroomSessionId"
        )) {
            request.remove(alias);
        }
    }

    private ObjectNode trace(ObjectNode request, String structure, String operation, AnimationDefinition definition) {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("version", VERSION);
        trace.put("protocol", "dsvp/1.0");
        trace.put("trace_id", traceId(request));
        trace.put("structure", structure);
        trace.put("operation", operation);
        trace.put("source_ref", request.path("source_ref").asText(""));
        ArrayNode steps = trace.putArray("steps");
        for (int index = 0; index < definition.steps().size(); index++) {
            AnimationStep animationStep = definition.steps().get(index);
            ObjectNode step = steps.addObject();
            step.put("step_id", index + 1);
            step.put("phase", "operation");
            step.put("title", animationStep.label());
            step.put("description", animationStep.note());
            ObjectNode action = step.putObject("action");
            action.put("type", animationStep.op());
            if (animationStep.value() != null) action.set("value", objectMapper.valueToTree(animationStep.value()));
        }
        trace.putArray("errors");
        trace.putArray("warnings");
        return trace;
    }

    private String traceId(JsonNode request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return "dsvp_" + HexFormat.of().formatHex(digest).substring(0, 20);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create DSVP trace id", error);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertArray(JsonNode node) {
        return List.copyOf((List<Object>) objectMapper.convertValue(node, List.class));
    }

    private Object scalar(JsonNode node, String field, boolean required) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            if (required) throw invalid("DSVP_VALUE_REQUIRED", field + " is required");
            return null;
        }
        if (node.isTextual()) return abbreviate(node.asText(), AnimationValidator.MAX_VALUE_LENGTH);
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.numberValue();
        throw invalid("DSVP_VALUE_INVALID", field + " must be scalar");
    }

    private int integer(JsonNode node, int fallback, int minimum, int maximum, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        if (!node.isIntegralNumber() || node.asInt() < minimum || node.asInt() > maximum) {
            throw invalid("DSVP_VALUE_INVALID", field + " is outside the valid range");
        }
        return node.asInt();
    }

    private Integer optionalInteger(JsonNode node, String field, int minimum, int maximum) {
        if (node == null || !node.has(field)) return null;
        return integer(node.get(field), 0, minimum, maximum, field);
    }

    private String optionalText(JsonNode node, String field, int maximum) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String value = node.get(field).asText("").trim();
        return value.isBlank() ? null : abbreviate(value, maximum);
    }

    private String text(JsonNode node, String field, int maximum) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank() || value.length() > maximum) {
            throw invalid("DSVP_REQUEST_INVALID", field + " is required");
        }
        return value;
    }

    private void requireObject(JsonNode value, String path) {
        if (value == null || !value.isObject()) {
            throw invalid("DSVP_REQUEST_INVALID", path + " must be an object");
        }
    }

    private void rejectUnknown(JsonNode value, Set<String> allowed, String path) {
        for (String name : value.propertyNames()) {
            if (!allowed.contains(name)) {
                throw invalid("DSVP_UNEXPECTED_FIELD", path + " contains an unsupported field");
            }
        }
    }

    private void validateSourceRef(JsonNode request) {
        if (!request.has("source_ref")) {
            return;
        }
        JsonNode sourceRef = request.get("source_ref");
        if (!sourceRef.isTextual()
                || sourceRef.asText().codePointCount(0, sourceRef.asText().length()) > MAXIMUM_SOURCE_REF_LENGTH) {
            throw invalid("DSVP_REQUEST_INVALID", "source_ref must be a string of at most 160 characters");
        }
    }

    private String abbreviate(String value, int maximum) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), maximum));
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
