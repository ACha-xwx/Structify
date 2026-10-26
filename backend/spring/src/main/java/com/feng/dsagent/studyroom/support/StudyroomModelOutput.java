package com.feng.dsagent.studyroom.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.feng.dsagent.studyroom.vo.StudyroomVO;
import com.feng.dsagent.studyroom.domain.StudyroomScene;
public record StudyroomModelOutput(
    int version,
    String title,
    String summary,
    List<String> evidenceIds,
    List<Step> steps
) {

    public static final int CURRENT_VERSION = 1;
    public static final int MAX_TITLE_LENGTH = 160;
    public static final int MAX_SUMMARY_LENGTH = 1_000;
    public static final int MAX_STEPS = 20;
    public static final int MAX_STEP_ID_LENGTH = 80;
    public static final int MAX_STEP_TITLE_LENGTH = 160;
    public static final int MAX_EXPLANATION_LENGTH = 4_000;
    public static final int MAX_EVIDENCE_IDS = 20;
    public static final int MAX_RESOURCES_PER_STEP = 14;
    public static final int MAX_ID_LENGTH = 160;
    public static final int MAX_JSON_LENGTH = 64_000;
    public static final int MAX_PARAMETER_DEPTH = 5;
    public static final int MAX_PARAMETER_ITEMS = 128;
    public static final int MAX_PARAMETER_STRING_LENGTH = 1_000;

    private static final Set<String> ROOT_FIELDS = Set.of("version", "title", "summary", "evidenceIds", "steps");
    private static final Set<String> STEP_FIELDS = Set.of("id", "type", "title", "explanation", "resources", "orchestration");
    private static final Set<String> RESOURCE_FIELDS = Set.of("type", "id", "parameters");

    public StudyroomModelOutput {
        require(version == CURRENT_VERSION, "Unsupported model output version");
        title = boundedText(title, MAX_TITLE_LENGTH, "title");
        summary = boundedText(summary, MAX_SUMMARY_LENGTH, "summary");
        evidenceIds = boundedIdentifiers(evidenceIds, MAX_EVIDENCE_IDS, "evidenceIds");
        steps = steps == null ? List.of() : List.copyOf(steps);
        require(!steps.isEmpty(), "steps must not be empty");
        require(steps.size() <= MAX_STEPS, "too many steps");
        Set<String> stepIds = new LinkedHashSet<>();
        for (Step step : steps) {
            require(stepIds.add(step.id()), "duplicate step id");
        }
    }

    /** Keep checkpoint JSON identical to the model protocol; Java enum names are not wire values. */
    public String toJson(ObjectMapper mapper) {
        var root=mapper.valueToTree(this);
        for(int i=0;i<steps.size();i++) {
            var node=(tools.jackson.databind.node.ObjectNode)root.path("steps").get(i);
            node.put("type",steps.get(i).type().value());
            for(int j=0;j<steps.get(i).resources().size();j++)
                ((tools.jackson.databind.node.ObjectNode)node.path("resources").get(j)).put("type",steps.get(i).resources().get(j).type().value());
            if(steps.get(i).orchestration()==null)node.remove("orchestration");
        }
        return mapper.writeValueAsString(root);
    }

    public static StudyroomModelOutput parse(ObjectMapper mapper, String json) {
        if (mapper == null || json == null || json.isBlank()) {
            throw new IllegalArgumentException("Model output must be non-empty JSON");
        }
        require(json.length() <= MAX_JSON_LENGTH, "model output is too large");
        try {
            JsonNode root = mapper.readTree(json);
            requireObject(root, "root");
            requireFields(root, ROOT_FIELDS, "root");
            int version = requiredInt(root, "version");
            String title = requiredText(root, "title");
            String summary = requiredText(root, "summary");
            List<String> evidenceIds = textArray(root.path("evidenceIds"), MAX_EVIDENCE_IDS, "evidenceIds");
            JsonNode stepNodes = root.path("steps");
            require(stepNodes.isArray(), "steps must be an array");
            require(stepNodes.size() <= MAX_STEPS, "too many steps");
            List<Step> steps = new ArrayList<>();
            for (JsonNode stepNode : stepNodes) {
                requireObject(stepNode, "step");
                normalizeCodeFields(stepNode);
                requireFields(stepNode, STEP_FIELDS, "step");
                StepType type = StepType.parse(requiredText(stepNode, "type"));
                JsonNode resourceNodes = stepNode.path("resources");
                require(resourceNodes.isArray(), "resources must be an array");
                require(resourceNodes.size() <= MAX_RESOURCES_PER_STEP, "too many resources in step");
                List<ResourceRef> resources = new ArrayList<>();
                for (JsonNode resourceNode : resourceNodes) {
                    requireObject(resourceNode, "resource");
                    requireFields(resourceNode, RESOURCE_FIELDS, "resource");
                    ResourceType resourceType = ResourceType.parse(requiredText(resourceNode, "type"));
                    require(stepNode.has("orchestration") || type.value().equals(resourceType.value()), "resource type must match step type");
                    JsonNode parameters = resourceNode.path("parameters");
                    require(parameters.isObject(), "resource parameters must be an object");
                    resources.add(new ResourceRef(
                        resourceType,
                        boundedIdentifier(requiredText(resourceNode, "id"), MAX_ID_LENGTH, "resource id"),
                        immutableJsonValue(parameters, 0)
                    ));
                }
                steps.add(new Step(
                    boundedIdentifier(requiredText(stepNode, "id"), MAX_STEP_ID_LENGTH, "step id"),
                    type,
                    boundedText(requiredText(stepNode, "title"), MAX_STEP_TITLE_LENGTH, "step title"),
                    boundedText(requiredText(stepNode, "explanation"), MAX_EXPLANATION_LENGTH, "explanation"),
                    resources,
                    stepNode.has("orchestration") ? StudyroomSceneAdapter.parse(mapper, mapper.writeValueAsString(stepNode.path("orchestration")),
                        resources.stream().map(r -> new StudyroomVO.Resource(r.type().value(), r.id(), "", "", r.parameters(), true)).toList()) : null
                ));
            }
            return new StudyroomModelOutput(version, title, summary, evidenceIds, steps);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid Studyroom model JSON (" + error.getClass().getSimpleName() + ")", error);
        }
    }

    public void validateEvidenceIds(Set<String> authorizedEvidenceIds) {
        Set<String> allowed = authorizedEvidenceIds == null ? Set.of() : Set.copyOf(authorizedEvidenceIds);
        if (!allowed.containsAll(evidenceIds)) {
            throw new IllegalArgumentException("Model output contains an unauthorized evidence id");
        }
    }

    // Only relocate known presentation fields; never infer a resource, permission, or executable code.
    private static void normalizeCodeFields(JsonNode step) {
        if (!step.has("orchestration")) return;
        List<JsonNode> codeScenes = new ArrayList<>();
        var primary = step.path("orchestration").path("primary");
        if ("C_CODE".equals(primary.path("kind").asString())) codeScenes.add(primary);
        for (var scene : step.path("orchestration").path("supplementary"))
            if ("C_CODE".equals(scene.path("kind").asString())) codeScenes.add(scene);
        for (String field : List.of("codeMode", "task", "stdin", "expectedObservation")) {
            if (!step.has(field)) continue;
            require(codeScenes.size() == 1, "Misplaced code fields require one unambiguous code scene");
            var target = (tools.jackson.databind.node.ObjectNode) codeScenes.getFirst();
            require(!target.hasNonNull(field) || target.path(field).equals(step.path(field)), "Conflicting code scene field: " + field);
            target.set(field, step.path(field));
            ((tools.jackson.databind.node.ObjectNode) step).remove(field);
        }
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt(), field + " must be an integer");
        return value.intValue();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isString(), field + " must be a string");
        return value.asString();
    }

    private static List<String> textArray(JsonNode node, int limit, String field) {
        require(node.isArray(), field + " must be an array");
        require(node.size() <= limit, field + " has too many values");
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            require(item.isString(), field + " values must be strings");
            values.add(boundedIdentifier(item.asString(), MAX_ID_LENGTH, field + " value"));
        }
        return boundedIdentifiers(values, limit, field);
    }

    private static void requireObject(JsonNode node, String field) {
        require(node != null && node.isObject(), field + " must be an object");
    }

    private static void requireFields(JsonNode node, Set<String> allowed, String field) {
        node.properties().forEach(entry -> require(allowed.contains(entry.getKey()), "unknown " + field + " field: " + entry.getKey()));
    }

    private static Object immutableJsonValue(JsonNode node, int depth) {
        require(depth <= MAX_PARAMETER_DEPTH, "resource parameters are too deeply nested");
        if (node.isObject()) {
            require(node.size() <= MAX_PARAMETER_ITEMS, "resource parameters contain too many entries");
            Map<String, Object> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), immutableJsonValue(entry.getValue(), depth + 1)));
            return Collections.unmodifiableMap(values);
        }
        if (node.isArray()) {
            require(node.size() <= MAX_PARAMETER_ITEMS, "resource parameter array is too large");
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(immutableJsonValue(value, depth + 1)));
            // Null denotes a missing child in the animation engine's level-order tree format.
            return Collections.unmodifiableList(values);
        }
        if (node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isString()) {
            String value = node.asString();
            require(value.length() <= MAX_PARAMETER_STRING_LENGTH, "resource parameter string is too long");
            return value;
        }
        throw new IllegalArgumentException("Unsupported JSON value in resource parameters");
    }

    private static List<String> boundedIdentifiers(List<String> values, int limit, String field) {
        List<String> result = values == null ? List.of() : List.copyOf(values);
        require(result.size() <= limit, field + " has too many values");
        Set<String> unique = new LinkedHashSet<>();
        for (String value : result) {
            require(unique.add(boundedIdentifier(value, MAX_ID_LENGTH, field + " value")), field + " contains duplicate values");
        }
        return result;
    }

    private static String boundedText(String value, int maxLength, String field) {
        require(value != null && !value.isBlank(), field + " must not be blank");
        String normalized = value.strip();
        require(normalized.length() <= maxLength, field + " is too long");
        return normalized;
    }

    private static String boundedIdentifier(String value, int maxLength, String field) {
        String normalized = boundedText(value, maxLength, field);
        require(normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"), field + " contains invalid characters");
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public record Step(String id, StepType type, String title, String explanation, List<ResourceRef> resources, StudyroomScene.Orchestration orchestration) {
        public Step(String id, StepType type, String title, String explanation, List<ResourceRef> resources) {
            this(id, type, title, explanation, resources, null);
        }
        public Step {
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }

    public record ResourceRef(ResourceType type, String id, Object parameters) {
    }

    public enum StepType {
        EXPLANATION("explanation"),
        PPT("ppt"),
        ANIMATION("animation"),
        C_CODE("c_code");

        private final String value;

        StepType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        private static StepType parse(String value) {
            for (StepType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown step type: " + value);
        }
    }

    public enum ResourceType {
        PPT("ppt"),
        ANIMATION("animation"),
        C_CODE("c_code");

        private final String value;

        ResourceType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        private static ResourceType parse(String value) {
            for (ResourceType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown resource type: " + value);
        }
    }
}
