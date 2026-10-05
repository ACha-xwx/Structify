package com.feng.dsagent.animation;

import com.feng.dsagent.classroom.ClassroomModelJson;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationIntentController.class);

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

    /**
     * {@code confirmed} marks a request where the learner has already said yes to an offer — the chat
     * page's "好的". The worth-judging is done: the model's only remaining job is to pick the capability,
     * and refusing again with "this is a concept, not a process" would break the promise just made.
     *
     * <p>{@code confirmed} is a {@link Boolean} rather than a primitive because a reply-only request
     * omits it, and Jackson maps the missing primitive to null - which would fail the whole body.
     *
     * <p>{@code reply} carries the learner's own words after such an offer. Reading them is a question of
     * meaning — "包的", "o而k之", "okok", "整一个", "why not", "👍" all mean yes and no word list can keep
     * up with the next coinage — so the model reads them, and answers with the demo or with
     * {@code declined}. Doing both in one call keeps a refused offer from costing two round trips.
     */
    /**
     * {@code chapterId} is optional on purpose. A learner who left the chapter selector on "全部章节" has
     * asked for the whole textbook, and narrowing that request to one chapter silently takes most demos
     * off the table - the chat page used to fall back to the first chapter, and a linked-list reversal
     * then came back as "linked_list 仅支持 append/delete/find/insert".
     */
    public record Input(
        @Size(max = 64) String chapterId,
        @Size(max = 2000) String prompt,
        JsonNode currentRequest,
        Boolean confirmed,
        @Size(max = 200) String reply
    ) {
        /** Absent means not confirmed, so the rest of the class never has to ask about null. */
        public Input {
            confirmed = confirmed != null && confirmed;
        }

        public Input(String chapterId, String prompt) {
            this(chapterId, prompt, null, false, null);
        }

        public Input(String chapterId, String prompt, JsonNode currentRequest, boolean confirmed) {
            this(chapterId, prompt, currentRequest, Boolean.valueOf(confirmed), null);
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
        String requestedChapter = input.chapterId() == null ? "" : input.chapterId().trim();
        String chapterTitle;
        if (requestedChapter.isEmpty()) {
            // No chapter chosen: the whole textbook is the scope, which is what "全部章节" means.
            chapterTitle = "全部章节";
        } else {
            var titles = jdbc.queryForList("SELECT title FROM chapters WHERE id=? AND status='PUBLISHED'", String.class, requestedChapter);
            if (titles.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在");
            chapterTitle = titles.getFirst();
        }

        String studentRequest = input.prompt() == null || input.prompt().isBlank()
            ? "选择一个本章适合观察过程的小例子"
            : input.prompt();
        JsonNode capabilityList = localCapabilities(requestedChapter);
        if (capabilityList != null) {
            return interpretThroughLocalEngine(user, requestedChapter, chapterTitle, studentRequest, input.currentRequest(), capabilityList, Boolean.TRUE.equals(input.confirmed()), input.reply());
        }
        return interpretWithModelRequest(user, requestedChapter, chapterTitle, studentRequest, input.currentRequest(), input.reply());
    }

    /** Chapter ids in this codebase start with the textbook chapter number ("06-tree"), which is how the engine scopes. */
    private JsonNode localCapabilities(String chapterId) {
        if (engine == null || !engine.enabled()) return null;
        return engine.capabilities(DsvpLocalEngine.chapterScope(chapterId)).orElse(null);
    }

    /**
     * Every capability the engine can really simulate, with no chapter scope applied.
     *
     * <p>Asked for lazily, and only where it is needed: the scoped table is what the model reads first, so
     * the ordinary call stays small. It is the answer to "the chapter has none of its own" and to "the
     * model says this cannot be done" - a learner who asks for a demo that belongs to another chapter must
     * get one, not a refusal that only ever looked at the current chapter.
     */
    private JsonNode wholeCatalogue() {
        if (engine == null || !engine.enabled()) return null;
        JsonNode whole = engine.capabilities("").orElse(null);
        return whole != null && !whole.path("prompt").asText("").isBlank() ? whole : null;
    }

    /** Primary path: the model picks a capability; the engine validates it and builds the executable request. */
    private JsonNode interpretThroughLocalEngine(
        AuthenticatedUser user,
        String chapterId,
        String chapterTitle,
        String studentRequest,
        JsonNode currentRequest,
        JsonNode capabilityList,
        boolean confirmed,
        String reply
    ) {
        String capabilities = capabilityList.path("prompt").asText("");
        if (capabilities.isBlank()) {
            // A chapter with no demos of its own - 绪论 - used to drop straight to the free-form path, which
            // puts no capability table in front of the model at all; it then improvised from memory. The
            // registry is the fact on both paths, so an empty chapter widens to the whole textbook rather
            // than throwing the registry away.
            JsonNode whole = wholeCatalogue();
            if (whole == null) return interpretWithModelRequest(user, chapterId, chapterTitle, studentRequest, currentRequest, reply);
            capabilityList = whole;
            capabilities = whole.path("prompt").asText("");
            chapterTitle = "整本教材（" + chapterTitle + " 这一章没有专属演示）";
        }

        boolean readingReply = reply != null && !reply.isBlank();
        ObjectNode context = mapper.createObjectNode();
        context.put("chapter", chapterTitle);
        context.put("studentRequest", studentRequest);
        if (readingReply) context.put("studentReply", reply);
        if (currentRequest != null && !currentRequest.isNull()) context.set("currentRequest", currentRequest);

        String judgement;
        if (confirmed) {
            judgement = """
                学生已经明确确认要看动画演示，不要再判断值不值得，但必须选**过程与需求一致**的能力：
                - 需求里点明了操作（逆置/反转、插入、删除、查找、排序、遍历…）时，只能选同名的操作；能力表里没有同名操作就返回 unsupported，
                  绝不能用同一个数据结构上的别的操作顶替。反例：把「单链表逆置」做成「头插法建表」或「按位查找」——步骤完全不同，是错的。
                - 需求只是一个主题（例如「栈」「二叉排序树」「图」）时，可以选这个结构上最能说明过程的一个操作。
                - 只有需求完全不在能力表内才返回 unsupported。
                """;
        } else if (readingReply) {
            judgement = """
                上面的 studentRequest 里有一句邀请学生看动画演示的话，学生针对这句邀请的回复是 studentReply。
                先读懂这句回复是不是答应看这个演示。答应的说法没有固定形式：方言、网络流行语、谐音、缩写、叠词、表情符号、外语、
                倒装都可能是答应（例如「包的」「o而k之」「okok」「好嘞」「整一个」「why not」「👍」都算答应）。
                - 是答应：不要再判断值不值得，直接按下面的规则选能力并给参数。
                - 不是答应（明确拒绝、提出了新的问题、或者要的是别的东西）：不要选能力，直接返回 declined 形状。
                """;
        } else {
            judgement = "静态定义、概念辨析、一句话能说清的问题不适合动画，此时把 needed 设为 false。";
        }
        String declinedShape = readingReply
            ? "{\"declined\":true,\"reason\":\"一句话说明为什么这句回复不是答应看演示\"}\n            "
            : "";
        JsonNode intent = model.generateFor(user.userId(), "animation-intent", """
            你是教学设计师。判断学生这次的需求值不值得用动画讲，并且只做两件事：从下面的真实能力表里选一个能力、给出它的参数。
            你绝不生成动画步骤、状态快照或数值序列——逐帧状态由本地确定性模拟器计算，你编出来的帧会被丢弃。
            %s
            真实可用能力（本节优先，格式为「能力名[必需参数]：说明」）：
            %s
            选择规则：
            1. 只能从上面的能力表里选，绝不能发明能力名；能力表之外的东西一律返回 unsupported。
            2. 参数优先从学生需求和当前教材内容里提取；没有依据的数字、序列、规模一律留空，不要编造数字。留空是允许的：服务端会回填该能力注册的标准教学示例，并明确标注它不是教材原例。
            3. 演示的操作必须和需求一致：学生要「逆置」就不能做成「插入」「查找」或「遍历」，要「查找」就不能做成「遍历」；也不要一次交换冒充排序、读取一个元素冒充查找、访问结点冒充旋转。宁可返回 unsupported，也不要给一个过程不同的动画。
            4. purpose 一句话说明这里为什么值得看动态过程。
            5. 判断只能以上面的能力表为唯一事实依据。表里有对应条目就必须选它，绝不能以「未实现」「这个结构只支持某某操作」为由拒绝。
            6. 不要凭印象描述某个结构支持或不支持哪些操作——印象可能过时或缺漏，能力表才是事实。返回 unsupported 之前，必须逐条扫一遍能力表，确认真的没有可用条目。
            7. **章节只是优先顺序，不是边界。** 学生要看的算法属于数据结构课程的内容、只是不在上面这张表里时（例如在「栈与队列」这一章里问折半查找、在「线性表」里问二叉树遍历），一律返回 unsupported —— 程序会立刻拿整本教材的能力表再核对一次并把候选补给它。
               绝不要以「不在本章」「不属于本结构」为由把需求判成 needed:false：needed 只回答「这个过程本身值不值得用动画讲」（静态定义、概念辨析才设 false），「能不能做」由能力表和程序决定。
            只返回下列形状之一：
            {"needed":true,"confidence":0.0到1.0,"capability":"能力名","purpose":"为什么值得演示","arguments":{}}
            {"needed":false,"confidence":0.0,"capability":"","purpose":"为什么不需要动画","arguments":{}}
            {"unsupported":true,"reason":"说明不在能力表内的原因"}
            %s""".formatted(judgement, capabilities, declinedShape), context.toString(), 900, this::validateIntent);

        if (intent.path("declined").asBoolean(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_DECLINED",
                intent.path("reason").asText("这不是答应看演示"));
        }
        if (intent.path("unsupported").asBoolean(false)) {
            String reason = intent.path("reason").asText("这个演示暂未实现");
            // A refusal has to be checked, not trusted. The engine really can build a binary search tree
            // insert and a linked-list reversal, and the model has refused both while claiming the
            // operations do not exist - a learner then gets told a demo is missing when it is right
            // there. So the closest entries are put back in front of it and it decides once more.
            //
            // The check reads the whole registry, not the scoped table: a chapter is a preference, not a
            // boundary, and a learner asking for a binary search demo while the lesson sits in the stack
            // chapter was told "not in this chapter's table" as if that settled it.
            JsonNode wider = wholeCatalogue();
            JsonNode recheck = recheckRefusal(user, chapterTitle, studentRequest, reply,
                wider == null ? capabilityList : wider, reason, wider != null);
            if (recheck != null && recheck.path("needed").asBoolean(false)) {
                LOGGER.info("animation intent: first answer refused ({}), the check found {} instead",
                    reason, recheck.path("capability").asText(""));
                intent = recheck;
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED", reason);
            }
        }

        if (intent.path("needed").isBoolean() && !intent.path("needed").asBoolean(false)) {
            // "Not needed" is also a judgement, and it too can be made without the facts. Asked to halve-search
            // while the lesson sat in the stack chapter, the model answered that the operation was not in that
            // chapter's table and therefore did not belong in its animations - a chapter boundary deciding for
            // it. The same wider check runs here, but only when the wider table offers a candidate the scoped
            // table did not: a concept question matches the same entries on both sides and costs no extra call.
            JsonNode wider = wholeCatalogue();
            if (wider != null && offersSomethingNew(studentRequest, reply, capabilityList, wider)) {
                JsonNode recheck = recheckRefusal(user, chapterTitle, studentRequest, reply, wider,
                    intent.path("purpose").asText("这个需求不需要动画"), true);
                if (recheck != null && recheck.path("needed").asBoolean(false)) {
                    LOGGER.info("animation intent: first answer said not needed ({}), the wider table found {}",
                        intent.path("purpose").asText(""), recheck.path("capability").asText(""));
                    intent = recheck;
                }
            }
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

    /**
     * Whether the wider table holds a candidate the scoped table did not. That is the only case worth a second
     * call: the scoped entries are a subset of the wider ones, so the candidates overlap completely unless the
     * request really reaches into another chapter.
     */
    private boolean offersSomethingNew(String studentRequest, String reply, JsonNode scoped, JsonNode wider) {
        String query = (studentRequest == null ? "" : studentRequest) + " " + (reply == null ? "" : reply);
        Set<String> known = new LinkedHashSet<>();
        for (JsonNode node : scoped.path("capabilities")) {
            known.add(node.path("capability").asText(""));
        }
        for (JsonNode node : candidateCapabilities(query, wider, 8)) {
            if (!known.contains(node.path("capability").asText(""))) return true;
        }
        return false;
    }

    /**
     * Asks once more before telling a learner that a demo does not exist, with the closest entries from
     * the engine's own table in front of the model. The table is the only evidence, and the model has
     * been seen to deny entries that were sitting in it.
     */
    private JsonNode recheckRefusal(
        AuthenticatedUser user,
        String chapterTitle,
        String studentRequest,
        String reply,
        JsonNode capabilityList,
        String reason,
        boolean wholeTextbook
    ) {
        List<JsonNode> candidates = candidateCapabilities(
            (studentRequest == null ? "" : studentRequest) + " " + (reply == null ? "" : reply),
            capabilityList,
            8
        );
        if (candidates.isEmpty()) return null;
        StringBuilder listing = new StringBuilder();
        for (JsonNode node : candidates) {
            listing.append(node.path("capability").asText(""))
                .append('[').append(String.join("|", textList(node.path("requiredArguments")))).append("]：")
                .append(node.path("label").asText("")).append('（')
                .append(node.path("description").asText("")).append("）\n");
        }
        ObjectNode context = mapper.createObjectNode();
        context.put("chapter", chapterTitle);
        context.put("studentRequest", studentRequest == null ? "" : studentRequest);
        if (reply != null && !reply.isBlank()) context.put("studentReply", reply);
        return model.generateFor(user.userId(), "animation-intent", """
            复核一次。学生要看的是：%s
            上一次的判断是「这次需求在能力表里没有对应条目」，理由写的是：%s
            %s
            下面这些条目（能力名[必需参数]：说明）是从那张表里按文字相似度找出来的：
            %s
            只看上面这些条目，判断有没有哪一条演示的过程**正是学生要看的那个过程**：
            - 有：返回 {"needed":true,"confidence":0.0到1.0,"capability":"能力名","purpose":"为什么值得演示","arguments":{}}
            - 没有：返回 {"unsupported":true,"reason":"说明这些条目为什么都不合适"}
            判断标准要严：同一种数据结构上的**不同操作不算「正是」**。学生要「插入」，条目里只有「遍历」或「访问」就必须继续返回 unsupported；
            要「查找」而条目只有「插入」也一样。绝不能为了让这次请求有结果就挑一个相近的操作顶替。
            反过来，只要有一条**正好就是他说的那个过程**（他要折半查找、条目就叫折半查找），就必须选它：
            「它属于别的章节」「它不是本结构的操作」都不是拒绝的理由——能力表里有，就说明做得到。
            这次不用判断值不值得动画，也不用找别的条目，只看上面列出的这些。只输出这两种形状之一。
            """.formatted(
                studentRequest == null ? "" : studentRequest,
                reason,
                wholeTextbook ? "注意：上面那次判断只看到「" + chapterTitle + "」这一张表；下面这些条目来自**整本教材**的能力表，包含本章以外的内容。" : "",
                listing.toString()
            ), context.toString(), 600, this::validateIntent);
    }

    /**
     * The entries most likely to be what the learner asked for, ranked by shared character pairs. It is
     * only ever a shortlist for the model to judge - never a decision of its own.
     */
    private List<JsonNode> candidateCapabilities(String request, JsonNode capabilityList, int limit) {
        Set<String> query = characterPairs(request);
        if (query.isEmpty()) return List.of();
        List<JsonNode> ranked = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (JsonNode node : capabilityList.path("capabilities")) {
            String text = node.path("label").asText("") + " " + node.path("description").asText("") + " "
                + node.path("capability").asText("") + " " + node.path("operation").asText("") + " "
                + node.path("textbook").asText("");
            int score = 0;
            for (String pair : characterPairs(text)) {
                if (query.contains(pair)) score++;
            }
            if (score == 0) continue;
            int at = 0;
            while (at < scores.size() && scores.get(at) >= score) at++;
            scores.add(at, score);
            ranked.add(at, node);
            if (ranked.size() > limit) {
                ranked.remove(ranked.size() - 1);
                scores.remove(scores.size() - 1);
            }
        }
        return ranked;
    }

    /** Character pairs stand in for words: Chinese is not spaced, so there is nothing to split on. */
    private static Set<String> characterPairs(String text) {
        String normalized = (text == null ? "" : text).toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[\\s，。！？、,.!?：:；;（）()\\[\\]{}\"'“”‘’~～]+", "");
        Set<String> pairs = new LinkedHashSet<>();
        for (int index = 0; index + 2 <= normalized.length(); index++) {
            pairs.add(normalized.substring(index, index + 2));
        }
        return pairs;
    }

    /** The model's own return shape, checked the same way on the first answer and on the recheck. */
    private void validateIntent(JsonNode json) {
        if (json.path("declined").asBoolean(false)) {
            ClassroomModelJson.requireText(json, "reason");
            return;
        }
        if (json.path("unsupported").asBoolean(false)) {
            ClassroomModelJson.requireText(json, "reason");
            return;
        }
        if (json.path("needed").asBoolean(false)) ClassroomModelJson.requireText(json, "capability");
        if (json.has("arguments") && !json.path("arguments").isObject()) {
            throw new IllegalArgumentException("$.arguments 必须是对象，键为参数名");
        }
    }

    /** Fallback path: no engine, so the model returns a whole DSVP request and the in-process simulator judges it. */
    private JsonNode interpretWithModelRequest(AuthenticatedUser user, String chapterId, String chapterTitle, String studentRequest, JsonNode currentRequest, String reply) {
        var context = mapper.createObjectNode();
        context.put("chapter", chapterTitle);
        context.put("studentRequest", studentRequest);
        if (reply != null && !reply.isBlank()) context.put("studentReply", reply);
        if (currentRequest != null && !currentRequest.isNull()) {
            simulator.adapt(currentRequest);
            context.set("currentRequest", currentRequest);
        }
        // The engine is down, so the model returns the whole request; reading the reply still happens
        // here, and a reply that is not an agreement must not be spent on a demo nobody asked for.
        String readingReply = reply == null || reply.isBlank() ? "" : """
            上面的 studentRequest 里有邀请学生看动画演示的话，studentReply 是学生针对邀请的回复。
            先判断这句回复是不是答应看演示（说法没有固定形式：「包的」「o而k之」「okok」「好嘞」「整一个」「why not」「👍」都算答应）。
            是答应就返回 DSVP 请求；不是答应就返回 {"declined":true,"reason":"一句话说明为什么这不是答应看演示"}。
            """;
        JsonNode request = model.generateFor(user.userId(), "animation-intent", """
            理解数据结构学习主题和学生要求，返回一个可执行 DSVP JSON 请求；不要生成动画帧。
            这是程序构造的教学小例子，不得冒称教材原例。只输出 version, structure, operation, params, initial_state 五个字段。
            格式 {"version":"1.0","structure":"stack","operation":"push","params":{"value":3,"capacity":12},"initial_state":{"data":[1,2]}}。容量取16，元素不超过8个。
            %s
            不能用一次交换冒充排序、用读取一个元素冒充查找算法或用访问结点冒充旋转。若目标算法不支持，返回 {"unsupported":true,"reason":"说明未实现范围"}。
            %s""".formatted(simulator.animationRules(chapterId), readingReply) + DsvpModelContract.INSTRUCTIONS, context.toString(), 1200, json -> {
            if (json.path("declined").asBoolean(false)) return;
            if (json.path("unsupported").asBoolean(false)) ClassroomModelJson.requireText(json, "reason");
            else simulator.adapt(json);
        });
        if (request.path("declined").asBoolean(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMATION_DECLINED",
                request.path("reason").asText("这不是答应看演示"));
        }
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
