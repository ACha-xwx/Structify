package com.feng.dsagent.animation;

import com.feng.dsagent.classroom.ClassroomModelJson;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Model understands the request; deterministic DSVP execution remains a separate operation.
 *
 * <p>Primary path: the model answers only "should this be animated, and if so which capability with which
 * arguments". The real capability list comes from the local engine scoped to the current textbook chapter,
 * and the engine — not the model — validates the arguments, fills in a canonical teaching example when the
 * source material carries no numbers, and later computes every frame.
 *
 * <p>Without a reachable engine the endpoint keeps its older behaviour (the model returns a whole DSVP
 * request validated in-process), so it degrades instead of disappearing.
 */
@RestController
@RequestMapping("/api/v1/animations")
public class AnimationIntentController {

    private static final String DEMO_SOURCE_REF = "系统标准教学示例（非教材原例）";

    private final ClassroomModelJson model;
    private final DsvpAnimationAdapter simulator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DsvpLocalEngine engine;

    /** Construction without the local engine: the model-request fallback path only. */
    public AnimationIntentController(ClassroomModelJson model, DsvpAnimationAdapter simulator, JdbcTemplate jdbc, ObjectMapper mapper) {
        this(model, simulator, jdbc, mapper, null);
    }

    @Autowired
    public AnimationIntentController(
        ClassroomModelJson model,
        DsvpAnimationAdapter simulator,
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        DsvpLocalEngine engine
    ) {
        this.model = model;
        this.simulator = simulator;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.engine = engine;
    }

    public record Input(@NotBlank @Size(max = 64) String chapterId, @Size(max = 2000) String prompt, JsonNode currentRequest) {
        public Input(String chapterId, String prompt) {
            this(chapterId, prompt, null);
        }
    }

    public record PlanInput(@NotBlank @Size(max = 64) String capability, JsonNode arguments, @Size(max = 160) String sourceRef) {}

    /**
     * Turns an explicitly chosen capability plus arguments into an executable DSVP request.
     *
     * <p>This is the animation lab's path and it never touches the model: a learner who picks "B 树插入"
     * from the picker has already made the semantic decision, so asking a model to guess it again would
     * only add latency and quota spend. The engine still validates the arguments, so the lab exercises the
     * same contract the classroom does, and the guidance about missing arguments comes from the engine.
     */
    @PostMapping("/plan")
    public JsonNode plan(@Valid @RequestBody PlanInput input) {
        if (engine == null || !engine.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DSVP_ENGINE_UNAVAILABLE", "本地动画引擎不可用");
        }
        ObjectNode intent = mapper.createObjectNode();
        intent.put("needed", true);
        // An explicit choice is not a guess, so confidence never filters it out here.
        intent.put("confidence", 1.0);
        intent.put("capability", input.capability());
        intent.set("arguments", input.arguments() == null || input.arguments().isNull()
            ? mapper.createObjectNode() : input.arguments());

        ObjectNode options = mapper.createObjectNode();
        options.put("allowDemoFallback", true);
        options.put("minimumConfidence", 0.0);
        options.put("demoSourceRef", DEMO_SOURCE_REF);
        options.put("sourceRef", input.sourceRef() == null || input.sourceRef().isBlank() ? DEMO_SOURCE_REF : input.sourceRef());

        JsonNode resolution = engine.resolve(intent, options).orElseThrow(() -> new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE, "DSVP_ENGINE_UNAVAILABLE", "本地动画引擎不可用"));
        if ("unsupported".equals(resolution.path("status").asText(""))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED",
                "能力表里没有 " + input.capability());
        }
        return resolution;
    }

    @PostMapping("/interpret")
    public JsonNode interpret(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Input input) {
        var titles = jdbc.queryForList("SELECT title FROM chapters WHERE id=? AND status='PUBLISHED'", String.class, input.chapterId());
        if (titles.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在");

        String studentRequest = input.prompt() == null || input.prompt().isBlank()
            ? "选择一个本章适合观察过程的小例子"
            : input.prompt();
        JsonNode capabilityList = localCapabilities(input.chapterId());
        if (capabilityList != null) {
            return interpretThroughLocalEngine(user, input.chapterId(), titles.getFirst(), studentRequest, input.currentRequest(), capabilityList);
        }
        return interpretWithModelRequest(user, input.chapterId(), titles.getFirst(), studentRequest, input.currentRequest());
    }

    /** Chapter ids in this codebase start with the textbook chapter number ("06-tree"), which is how the engine scopes. */
    private JsonNode localCapabilities(String chapterId) {
        if (engine == null || !engine.enabled()) return null;
        return engine.capabilities(DsvpLocalEngine.chapterScope(chapterId)).orElse(null);
    }

    /** Primary path: the model picks a capability; the engine validates it and builds the executable request. */
    private JsonNode interpretThroughLocalEngine(
        AuthenticatedUser user,
        String chapterId,
        String chapterTitle,
        String studentRequest,
        JsonNode currentRequest,
        JsonNode capabilityList
    ) {
        String capabilities = capabilityList.path("prompt").asText("");
        if (capabilities.isBlank()) return interpretWithModelRequest(user, chapterId, chapterTitle, studentRequest, currentRequest);

        ObjectNode context = mapper.createObjectNode();
        context.put("chapter", chapterTitle);
        context.put("studentRequest", studentRequest);
        if (currentRequest != null && !currentRequest.isNull()) context.set("currentRequest", currentRequest);

        JsonNode intent = model.generateFor(user.userId(), "animation-intent", """
            你是教学设计师。判断学生这次的需求值不值得用动画讲，并且只做两件事：从下面的真实能力表里选一个能力、给出它的参数。
            你绝不生成动画步骤、状态快照或数值序列——逐帧状态由本地确定性模拟器计算，你编出来的帧会被丢弃。
            静态定义、概念辨析、一句话能说清的问题不适合动画，此时把 needed 设为 false。
            真实可用能力（当前教材章，格式为「能力名[必需参数]：说明」）：
            %s
            选择规则：
            1. 只能从上面的能力表里选，绝不能发明能力名；能力表之外的东西一律返回 unsupported。
            2. 参数优先从学生需求和当前教材内容里提取；没有依据的数字、序列、规模一律留空，不要编造数字。留空是允许的：服务端会回填该能力注册的标准教学示例，并明确标注它不是教材原例。
            3. 不要用一次交换冒充排序、用读取一个元素冒充查找、用访问结点冒充旋转。
            4. purpose 一句话说明这里为什么值得看动态过程。
            只返回下面三种形状之一：
            {"needed":true,"confidence":0.0到1.0,"capability":"能力名","purpose":"为什么值得演示","arguments":{}}
            {"needed":false,"confidence":0.0,"capability":"","purpose":"为什么不需要动画","arguments":{}}
            {"unsupported":true,"reason":"说明不在能力表内的原因"}
            """.formatted(capabilities), context.toString(), 900, json -> {
            if (json.path("unsupported").asBoolean(false)) {
                ClassroomModelJson.requireText(json, "reason");
                return;
            }
            if (json.path("needed").asBoolean(false)) ClassroomModelJson.requireText(json, "capability");
            if (json.has("arguments") && !json.path("arguments").isObject()) {
                throw new IllegalArgumentException("$.arguments 必须是对象，键为参数名");
            }
        });

        if (intent.path("unsupported").asBoolean(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED",
                intent.path("reason").asText("这个演示暂未实现"));
        }

        ObjectNode options = mapper.createObjectNode();
        options.put("allowDemoFallback", true);
        options.put("minimumConfidence", 0.6);
        options.put("demoSourceRef", DEMO_SOURCE_REF);
        options.put("sourceRef", chapterTitle);
        JsonNode resolution = engine.resolve(intent, options).orElseThrow(() -> new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE, "DSVP_ENGINE_UNAVAILABLE", "本地动画引擎不可用，请稍后再试"));

        String status = resolution.path("status").asText("unsupported");
        if ("ready".equals(status)) {
            JsonNode request = resolution.path("toolRequest").path("request");
            if (request.isObject()) return request;
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_REQUEST_INVALID", "本地引擎没有给出可执行请求");
        }
        if ("not-needed".equals(status) || "low-confidence".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_NEEDED",
                intent.path("purpose").asText("这个问题不需要动画演示"));
        }
        if ("missing-arguments".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_ARGUMENTS_MISSING",
                "缺少参数：" + String.join("、", textList(resolution.path("missingArguments"))));
        }
        if ("invalid-arguments".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_REQUEST_INVALID",
                resolution.path("error").asText("参数不合法"));
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED",
            "能力表里没有 " + intent.path("capability").asText("这个演示"));
    }

    /** Fallback path: no engine, so the model returns a whole DSVP request and the in-process simulator judges it. */
    private JsonNode interpretWithModelRequest(AuthenticatedUser user, String chapterId, String chapterTitle, String studentRequest, JsonNode currentRequest) {
        var context = mapper.createObjectNode();
        context.put("chapter", chapterTitle);
        context.put("studentRequest", studentRequest);
        if (currentRequest != null && !currentRequest.isNull()) {
            simulator.adapt(currentRequest);
            context.set("currentRequest", currentRequest);
        }
        JsonNode request = model.generateFor(user.userId(), "animation-intent", """
            理解数据结构学习主题和学生要求，返回一个可执行 DSVP JSON 请求；不要生成动画帧。
            这是程序构造的教学小例子，不得冒称教材原例。只输出 version, structure, operation, params, initial_state 五个字段。
            格式 {"version":"1.0","structure":"stack","operation":"push","params":{"value":3,"capacity":12},"initial_state":{"data":[1,2]}}。容量取16，元素不超过8个。
            %s
            不能用一次交换冒充排序、用读取一个元素冒充查找算法或用访问结点冒充旋转。若目标算法不支持，返回 {"unsupported":true,"reason":"说明未实现范围"}。
            """.formatted(simulator.animationRules(chapterId)) + DsvpModelContract.INSTRUCTIONS, context.toString(), 1200, json -> {
            if (json.path("unsupported").asBoolean(false)) ClassroomModelJson.requireText(json, "reason");
            else simulator.adapt(json);
        });
        if (request.path("unsupported").asBoolean(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED", request.path("reason").asText());
        }
        return request;
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) values.add(item.asText(""));
        }
        return values;
    }
}
