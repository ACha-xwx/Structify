package com.feng.dsagent.classroom;

import com.feng.dsagent.animation.AnimationValidator;
import com.feng.dsagent.animation.DsvpAnimationAdapter;
import com.feng.dsagent.common.ApiException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class ClassroomScriptParser {

    private static final Set<String> STEP_TYPES = Set.of(
        "explain", "question", "discussion", "blackboard", "summary"
    );
    private static final Set<String> ROLES = Set.of("teacher", "assistant", "student");
    /** Where a question came from: the model's own design, the reviewed textbook, or a courseware page. */
    private static final Set<String> QUESTION_SOURCES = Set.of("model", "textbook", "slide");
    private static final Set<String> DSVP_PROTOCOLS = Set.of("dsvp/1", "dsvp/1.0");
    private static final String CURRENT_DSVP_PROTOCOL = "dsvp/1.0";
    private static final java.util.regex.Pattern SLIDE_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9._-]{1,160}$");

    private final ObjectMapper objectMapper;
    private final DsvpAnimationAdapter dsvp;

    public ClassroomScriptParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.dsvp = new DsvpAnimationAdapter(objectMapper, new AnimationValidator());
    }

    public ClassroomScriptPlan parse(String scriptJson) {
        try {
            JsonNode root = objectMapper.readTree(scriptJson);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            if (root.path("steps").isArray()) {
                return parseSteps(root);
            }
            if (root.path("stages").isObject()) {
                return parseLegacy(root.path("stages"));
            }
            throw invalid();
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw invalid();
        }
    }

    private ClassroomScriptPlan parseSteps(JsonNode root) {
        String lessonId = requiredText(root, "lessonId", 64);
        String title = requiredText(root, "title", 200);
        List<String> objectives = textArray(root.path("objectives"), "objectives", false, 12, 160);
        JsonNode steps = root.path("steps");
        // A courseware lesson carries one teaching step per page plus its questions, and the longest decks
        // in use run to 36 pages, so the guard has to sit above the shape a real lesson has: a limit of 40
        // predates interleaved questions and would reject a fully walked deck.
        if (steps.isEmpty() || steps.size() > 80) {
            throw problem("steps 必须是 1 至 80 个步骤的数组，当前 " + steps.size() + " 个");
        }

        List<JsonNode> normalizedSteps = new ArrayList<>();
        JsonNode question = null;
        for (JsonNode step : steps) {
            // The model occasionally returns the controlled vocabulary in a different case ("Explain", "QUESTION").
            // Normalise those two fields first so a casing slip cannot make a whole lesson unteachable;
            // anything outside the vocabulary is still rejected below.
            JsonNode copy = normalizeControlledText(step);
            try { validateStep(copy); }
            catch (ApiException error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", "$.steps[" + normalizedSteps.size() + "]: " + error.getMessage()); }
            normalizedSteps.add(copy);
            if (question == null && "question".equals(copy.path("type").asText())) {
                question = copy;
            }
        }

        EnumMap<ClassroomState, JsonNode> stages = new EnumMap<>(ClassroomState.class);
        ObjectNode opening = objectMapper.createObjectNode();
        opening.put("type", "opening");
        opening.put("lessonId", lessonId);
        opening.put("title", title);
        opening.set("objectives", root.path("objectives").isArray()
            ? root.path("objectives").deepCopy()
            : objectMapper.createArrayNode());
        stages.put(ClassroomState.OPENING, opening);

        JsonNode explanation = first(normalizedSteps, "explain");
        JsonNode publicQuestion = publicQuestion(question);
        stages.put(ClassroomState.EXPLAIN, nodeOrEmpty(explanation));
        stages.put(ClassroomState.QUESTION, nodeOrEmpty(publicQuestion));
        stages.put(ClassroomState.WAITING, nodeOrEmpty(publicQuestion));
        stages.put(ClassroomState.DISCUSS, nodeOrEmpty(first(normalizedSteps, "discussion")));
        JsonNode blackboard = first(normalizedSteps, "blackboard");
        if (blackboard == null) {
            blackboard = normalizedSteps.stream()
                .filter(step -> step.hasNonNull("animationRef") || step.hasNonNull("codeRef"))
                .findFirst()
                .orElse(null);
        }
        stages.put(ClassroomState.BLACKBOARD, nodeOrEmpty(blackboard));
        JsonNode summary = first(normalizedSteps, "summary");
        if (summary == null) {
            ObjectNode generated = objectMapper.createObjectNode();
            generated.put("type", "summary");
            generated.put("title", title + "课堂总结");
            summary = generated;
        }
        stages.put(ClassroomState.SUMMARY, summary);
        return new ClassroomScriptPlan(
            lessonId,
            title,
            objectives,
            stages,
            question == null ? objectMapper.createObjectNode() : question,
            false
        );
    }

    private ClassroomScriptPlan parseLegacy(JsonNode stagesNode) {
        EnumMap<ClassroomState, JsonNode> stages = new EnumMap<>(ClassroomState.class);
        for (ClassroomState state : ClassroomState.values()) {
            JsonNode stage = stagesNode.path(state.name());
            stages.put(state, stage.isObject() ? stage.deepCopy() : objectMapper.createObjectNode());
        }
        return new ClassroomScriptPlan(
            "legacy",
            "",
            List.of(),
            stages,
            objectMapper.createObjectNode(),
            true
        );
    }

    /**
     * Copies the step and lower-cases the two closed-vocabulary fields so a casing slip from the model
     * does not fail the whole script. Unknown values are still rejected by {@link #validateStep(JsonNode)}.
     */
    private JsonNode normalizeControlledText(JsonNode step) {
        JsonNode copy = step.deepCopy();
        if (copy.isObject()) {
            ObjectNode object = (ObjectNode) copy;
            lowerCaseInPlace(object, "type");
            lowerCaseInPlace(object, "role");
        }
        return copy;
    }

    private void lowerCaseInPlace(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value != null && value.isTextual()) {
            object.put(field, value.asText().trim().toLowerCase(Locale.ROOT));
        }
    }

    private void validateStep(JsonNode step) {
        if (!step.isObject()) {
            throw problem("步骤必须是 JSON 对象");
        }
        String type = requiredText(step, "type", 32);
        if (!STEP_TYPES.contains(type)) {
            // Echo the offending value so the repair round can actually fix it instead of guessing.
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID",
                "type 必须使用小写 explain/question/discussion/blackboard/summary，当前值为 \"" + type + "\"");
        }
        if (step.has("role")) {
            String role = requiredText(step, "role", 32);
            if (!ROLES.contains(role)) {
                throw problem("role 必须是 teacher、assistant 或 student，当前值为 \"" + role + "\"");
            }
        }
        if (step.hasNonNull("animationRef")) {
            validateAnimationRef(step.path("animationRef"));
        }
        if (step.has("slideScope")) {
            JsonNode scope = step.path("slideScope");
            if (!scope.isObject()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", "slideScope 必须是 {subLessonId, scene} 对象");
            }
            requiredText(scope, "subLessonId", 64);
            requiredText(scope, "scene", 64);
        }
        if (step.has("slideRefs")) {
            validateSlideRefs(step.path("slideRefs"));
        }
        if ("question".equals(type)) {
            requiredText(step, "prompt", 1000);
            textArray(step.path("expected"), "expected", true, 10, 200);
            textArray(step.path("misconceptions"), "misconceptions", false, 20, 200);
            // Optional here - whether a lesson must label its questions is decided where the lesson is known -
            // but a value outside the vocabulary is always a mistake worth echoing back.
            if (step.has("questionSource")) {
                String source = requiredText(step, "questionSource", 32);
                if (!QUESTION_SOURCES.contains(source)) {
                    throw problem("questionSource 必须是 model、textbook 或 slide，当前值为 \"" + source + "\"");
                }
            }
        } else if ("explain".equals(type)
                && !hasText(step, "content")
                && !hasText(step, "contentRef")
                && !hasAnimationRef(step.path("animationRef"))
                && !hasText(step, "codeRef")) {
            throw problem("explain 步骤必须有 content、contentRef、animationRef 或 codeRef 之一");
        }
    }

    private void validateAnimationRef(JsonNode reference) {
        if (reference.isTextual()) {
            String value = reference.asText().trim();
            if (value.isBlank() || value.length() > 64) throw invalid();
            return;
        }
        if (!reference.isObject() || !DSVP_PROTOCOLS.contains(reference.path("protocol").asText())) {
            throw invalid();
        }
        boolean validId = reference.path("animationId").isTextual()
            && !reference.path("animationId").asText().isBlank()
            && reference.path("animationId").asText().length() <= 120;
        boolean validRequest = false;
        if (reference.path("request").isObject()) {
            try {
                dsvp.adapt(reference.path("request"));
                validRequest = true;
            } catch (RuntimeException error) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", "animationRef.request: " + error.getMessage());
            }
        }
        if (!validId && !validRequest) throw invalid();
        ((ObjectNode) reference).put("protocol", CURRENT_DSVP_PROTOCOL);
    }

    private boolean hasAnimationRef(JsonNode value) {
        return value != null && (value.isObject() || (value.isTextual() && !value.asText().isBlank()));
    }

    /**
     * Slides a step may show. Only the shape is checked here; whether the ids exist for this lesson is
     * decided where the lesson is known (preparation), because the parser also serves legacy scripts.
     */
    private void validateSlideRefs(JsonNode references) {
        if (!references.isArray() || references.isEmpty() || references.size() > 3) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", "slideRefs 必须是 1 至 3 个幻灯片ID的数组");
        }
        for (JsonNode reference : references) {
            String value = reference.isTextual() ? reference.asText().trim() : "";
            if (value.isBlank() || value.length() > 160 || !SLIDE_ID.matcher(value).matches()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID",
                    "slideRefs 只允许课件ID，当前值为 \"" + (reference.isTextual() ? reference.asText() : reference.toString()) + "\"");
            }
        }
    }

    private JsonNode publicQuestion(JsonNode question) {
        if (question == null || !question.isObject()) {
            return null;
        }
        ObjectNode visible = (ObjectNode) question.deepCopy();
        visible.remove("expected");
        visible.remove("misconceptions");
        visible.remove("misconceptionFeedback");
        return visible;
    }

    private JsonNode first(List<JsonNode> steps, String type) {
        return steps.stream().filter(step -> type.equals(step.path("type").asText())).findFirst().orElse(null);
    }

    private JsonNode nodeOrEmpty(JsonNode value) {
        return value == null ? objectMapper.createObjectNode() : value.deepCopy();
    }

    private String requiredText(JsonNode node, String field, int maximumLength) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : "";
        if (value.isBlank() || value.codePointCount(0, value.length()) > maximumLength) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", field + " 必须是非空字符串，长度不超过 " + maximumLength);
        }
        return value;
    }

    /**
     * Rejects an array of short strings and says which one broke it. A bare "format invalid" leaves the
     * repair round with nothing to fix, and the model then repeats the very same mistake three times.
     */
    private List<String> textArray(JsonNode values, String field, boolean required, int maximumItems, int maximumLength) {
        if (!values.isArray()) {
            if (required) {
                throw problem(field + " 必须是字符串数组");
            }
            return List.of();
        }
        if (required && values.isEmpty()) {
            throw problem(field + " 不能为空数组");
        }
        if (values.size() > maximumItems) {
            throw problem(field + " 最多 " + maximumItems + " 项，当前 " + values.size() + " 项");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw problem(field + " 的每一项都必须是字符串，当前出现 " + shorten(value.toString()));
            }
            String normalized = value.asText().trim();
            if (normalized.isBlank()) {
                throw problem(field + " 不允许空字符串");
            }
            if (normalized.codePointCount(0, normalized.length()) > maximumLength) {
                throw problem(field + " 每一项不超过 " + maximumLength + " 字，当前为 " + shorten(normalized));
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String shorten(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 60 ? "\"" + text + "\"" : "\"" + text.substring(0, 60) + "…\"";
    }

    /** One validation failure, reported with the field it happened in and echoed back to the model. */
    private ApiException problem(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", message);
    }

    private boolean hasText(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank();
    }

    private ApiException invalid() {
        return new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CLASSROOM_SCRIPT_INVALID",
            "课堂脚本格式无效"
        );
    }
}
