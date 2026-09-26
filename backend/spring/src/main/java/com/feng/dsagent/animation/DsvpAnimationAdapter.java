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
     * The animation section of a teaching prompt: what this platform can really animate, right now.
     *
     * <p>Prompts used to carry a written-out list of operations each, and every one of them had drifted
     * from the code they described: they told the model that Dijkstra, AVL trees, B trees and external
     * sorting were not implemented, when the engine simulates all of them, while offering {@code array},
     * {@code heap} and {@code hash}, which the engine rejects. Both halves matter, because there are two
     * execution paths: the engine serves the textbook's own structures, and the in-process simulator
     * serves the generic ones. The offer below is the union, so a lesson never loses an animation the
     * platform can actually draw.
     *
     * <p>The shape rules are the ones the simulators accept, not a guess: all 167 engine capabilities were
     * driven end to end from their own teaching example through intent resolution into the simulator, so a
     * model that follows them produces a request the validator accepts.
     */
    public String animationRules(String chapterOrLessonId) {
        String catalogue = engine == null ? "" : engine.catalogueFor(chapterOrLessonId);
        StringBuilder rules = new StringBuilder();
        if (catalogue.isBlank()) {
            // A dead engine is not "no animations": the simulator below still draws them, so the prompt
            // only loses the chapter-specific half instead of being told to give up on animation.
            rules.append("本机动画引擎当前不可用，本次只使用下面这套内建演示。\n");
        } else {
            rules.append("可做动画的操作（本教材章的真实能力，来自动画引擎的能力表，格式为「结构.操作[必需参数]：说明」）：\n")
                .append(catalogue).append('\n');
        }
        rules.append("内建演示（第二条执行路径）支持：").append(fallbackOperations()).append("；两处都出现的结构用法相同。\n")
            .append("写 animationRef 的规则：structure 与 operation 必须取自上面两处清单，不能使用或发明清单外的操作；")
            .append("initial_state.data 放 initialData（树与哈夫曼树放 parent 或 runs；顺序表的 merge 放 [左表, 右表]），其余参数原样放进 params；")
            .append("顺序表的插入与删除位置用 params.position（从 1 开始），array 与 heap 的位置用 params.index（从 0 开始）；")
            .append("图的顶点放 params.nodes、边放 params.edges（形如 [起点, 终点, 权值]，端点可写顶点值也可写下标）、起点放 params.start；")
            .append("树是层序数组、空位用 null；单次初始元素最多 16 个。");
        return rules.toString();
    }

    /**
     * The structures the in-process simulator serves, read from the very table that accepts or rejects a
     * request, so this offer cannot drift from what actually runs. Sorted, so the prompt is stable.
     */
    private String fallbackOperations() {
        return new java.util.TreeMap<>(OPERATIONS).entrySet().stream()
            .map(entry -> entry.getKey() + " " + String.join("/", new java.util.TreeSet<>(entry.getValue())))
            .collect(java.util.stream.Collectors.joining("；"));
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
        if (local != null) return withClientContext(local, input);

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

    /**
     * Puts the caller's own context back onto a request the local engine echoed.
     *
     * <p>The engine returns its own normalized request and never carries the {@code context} block:
     * context is a Java-side concern (which chapter, lesson, classroom session or presentation page the
     * animation belongs to, and which reviewed resource it cites), so it is not forwarded. The evidence
     * layer authorizes by reading exactly that block from the executed request, and an echo without it
     * means "the caller named no source" — a team-only animation stayed readable through the API channel
     * and a classroom run silently stopped being recorded as evidence. Restoring the block keeps one
     * request shape for authorization, for the evidence hashes and for what the caller sees back.
     */
    private DsvpSimulationResponse withClientContext(DsvpSimulationResponse response, JsonNode input) {
        ObjectNode context = canonicalContext(input);
        if (context.isEmpty() || !response.request().isObject()) return response;
        ObjectNode request = (ObjectNode) response.request().deepCopy();
        request.set("context", context);
        return new DsvpSimulationResponse(
            response.protocol(),
            request,
            response.trace(),
            response.animationData(),
            response.recordId()
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
        ObjectNode context = canonicalContext(request);
        if (context.isEmpty()) {
            request.remove("context");
        } else {
            request.set("context", context);
        }
        removeAliases(request);
    }

    /**
     * The single context block a client request means: whatever it sent under {@code context}, plus the
     * top-level aliases of the same fields, plus a top-level {@code source_ref}.
     *
     * <p>Exactly one shape is derived here so the three consumers of a context cannot disagree: the
     * evidence layer authorizes from it, the executed request is hashed and snapshotted with it, and the
     * caller's echo carries it.
     */
    private ObjectNode canonicalContext(JsonNode request) {
        ObjectNode context = objectMapper.createObjectNode();
        JsonNode given = request.path("context");
        if (given.isObject()) {
            for (String field : CONTEXT_FIELDS) {
                if (given.has(field)) context.set(field, given.get(field).deepCopy());
            }
        }
        copyAlias(request, context, "chapter_id", "chapterId");
        copyAlias(request, context, "lesson_id", "lessonId");
        copyAlias(request, context, "presentation_id", "presentationId");
        copyAlias(request, context, "presentation_page_id", "presentationPageId");
        copyAlias(request, context, "classroom_session_id", "classroomSessionId");
        if (!context.has("source_ref") && request.has("source_ref")) {
            context.set("source_ref", request.get("source_ref").deepCopy());
        }
        return context;
    }

    private void copyAlias(JsonNode request, ObjectNode context, String canonical, String alias) {
        if (context.has(canonical)) return;
        JsonNode value = request.has(alias) ? request.get(alias) : request.get(canonical);
        if (value != null && !value.isNull()) context.set(canonical, value.deepCopy());
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
