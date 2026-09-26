package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The part validator is where a model answer for one part of a courseware lesson is accepted or refused.
 * Its promise to the classroom is that a teaching step teaches the page its position dictates and that the
 * range a step belongs to follows from that page - neither of which the model is asked to transcribe any
 * more, because a hand-copied range once cost a whole 42-page deck its lesson.
 */
class ClassroomPreparationPartTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClassroomScriptParser parser = new ClassroomScriptParser(new ObjectMapper());

    private static SlideSpinePlan.Slide page(String id, int number, String title, String subLessonId, String scene) {
        return new SlideSpinePlan.Slide(id, number, title, "本页正文", "本页要点", "内容", "8.3.7",
            subLessonId, "B 树查找", scene, List.of());
    }

    private static List<Map<String, Object>> textbookRows() {
        return List.of(
            Map.of("id", "c1", "content", "8.3.7 查找性能分析\nB 树的查找效率与树高有关。", "page_label", "第 320 页"),
            Map.of("id", "c2", "content", "8.3.7 查找性能分析\nB 树每个结点存放多个关键字。", "page_label", "第 321 页"));
    }

    /**
     * The same lesson with a worked example on a page no courseware covers, so the local spine has something
     * to append as an extension step.
     */
    private static List<Map<String, Object>> rowsWithWorkedExample() {
        return List.of(
            Map.of("id", "c1", "content", "8.3.7 查找性能分析\nB 树的查找效率与树高有关。", "page_label", "第 320 页"),
            Map.of("id", "c2", "content", "【算法描述】B 树每个结点存放多个关键字。", "page_label", "第 321 页"));
    }

    /** One part of a lesson, exactly as {@code prepare} hands it to the validator. */
    private ClassroomPreparation.PartRules rules(List<SlideSpinePlan.Slide> spine, int partSize) {
        List<Map<String, Object>> chunks = textbookRows();
        Map<String, String[]> scopeOfSlide = new LinkedHashMap<>();
        spine.forEach(slide -> scopeOfSlide.put(slide.id(), new String[] {slide.subLessonId(), slide.scene()}));
        List<SlideSpinePlan.Slide> part = spine.subList(0, Math.min(spine.size(), partSize));
        return new ClassroomPreparation.PartRules("lesson-1", spine, part, 0, 1, 1, scopeOfSlide,
            scopeOfSlide.keySet(), Set.of("c1", "c2"), chunks, LessonPassageIndex.of(chunks));
    }

    /** A lesson without courseware: the pages the model was shown are the only pages it may name. */
    private ClassroomPreparation.PartRules rulesWithoutSpine() {
        List<Map<String, Object>> chunks = textbookRows();
        Map<String, String[]> scopeOfSlide = new LinkedHashMap<>();
        scopeOfSlide.put("deck-a-s001", new String[] {"08-04A", "concept"});
        scopeOfSlide.put("deck-a-s002", new String[] {"08-04B", "practice"});
        return new ClassroomPreparation.PartRules("lesson-1", List.of(), List.of(), 0, 1, 1, scopeOfSlide,
            scopeOfSlide.keySet(), Set.of("c1", "c2"), chunks, LessonPassageIndex.of(chunks));
    }

    private ArrayNode validate(String json, ClassroomPreparation.PartRules rules) throws Exception {
        JsonNode answer = mapper.readTree(json);
        return ClassroomPreparation.validatePart(answer, rules, parser, mapper);
    }

    @Test
    void theRangeOfAStepIsDerivedFromThePageItShows() throws Exception {
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        ArrayNode steps = validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解第一页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s001"],"slideScope":{"subLessonId":"瞎写的","scene":"瞎写的"}},
            {"type":"question","role":"teacher","prompt":"B 树查找与树高有什么关系？","expected":["树高越低越快"],
             "keywords":["树高"],"sourceChunkIds":["c1"],"questionSource":"model","slideRefs":["deck-a-s001"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["结点"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]}]}
            """, rules(spine, 12));

        // The model's own range is overwritten: the page decides, so a wrong hand-copy cannot survive.
        assertThat(steps.get(0).path("slideScope").path("subLessonId").asText()).isEqualTo("08-04A");
        assertThat(steps.get(0).path("slideScope").path("scene").asText()).isEqualTo("concept");
        // A question asks about the page already on screen, so it repeats that page and stays in its range.
        assertThat(steps.get(1).path("slideScope").path("subLessonId").asText()).isEqualTo("08-04A");
        assertThat(steps.get(1).path("slideRefs").get(0).asText()).isEqualTo("deck-a-s001");
        assertThat(steps.get(2).path("slideScope").path("scene").asText()).isEqualTo("practice");
        // Every step points at exactly one page, in the order the deck authored them.
        assertThat(steps.get(2).path("slideRefs")).hasSize(1);
        // Provenance is still reported for a model-written step, in the same vocabulary as the local spine.
        assertThat(steps.get(0).path("textbookMatch").asText()).isEqualTo("exact");
    }

    @Test
    void aStepThatSkipsAPageIsRefusedWithThePageItMustTeach() throws Exception {
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        assertThatThrownBy(() -> validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s002"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s002"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["B 树"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]}]}
            """, rules(spine, 12)))
            .hasMessageContaining("必须讲本段第 1 页 deck-a-s001")
            .hasMessageContaining("slideRefs 请写成 [\"deck-a-s001\"]");
    }

    @Test
    void aCasingSlipDoesNotTurnAQuestionIntoAPageStep() throws Exception {
        // "QUESTION" used to read as a step that teaches the next page, which then failed the page promise
        // and cost the whole part - a capital letter is not a teaching decision.
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        ArrayNode steps = validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"Explain","role":"Teacher","content":"讲解第一页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s001"]},
            {"type":"QUESTION","role":"Teacher","prompt":"B 树查找与树高有什么关系？","expected":["树高越低越快"],
             "keywords":["树高"],"sourceChunkIds":["c1"],"questionSource":"model","slideRefs":["deck-a-s001"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["结点"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]}]}
            """, rules(spine, 12));

        assertThat(steps.get(0).path("type").asText()).isEqualTo("explain");
        assertThat(steps.get(1).path("type").asText()).isEqualTo("question");
        assertThat(steps.get(2).path("slideRefs").get(0).asText()).isEqualTo("deck-a-s002");
    }

    @Test
    void aPartThatNeverAsksAnythingIsRefused() throws Exception {
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        assertThatThrownBy(() -> validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解第一页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s001"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["结点"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]},
            {"type":"explain","role":"teacher","content":"教材延伸","keywords":["延伸"],"sourceChunkIds":["c2"]}]}
            """, rules(spine, 12)))
            .hasMessageContaining("至少 1 个 type=question");
    }

    @Test
    void beyondTheLastPageAStepMayNotNameAPageAtAll() throws Exception {
        // The pages of a part are consumed in order, so a step after the last page is textbook extension
        // work that deliberately leaves the deck where it is.
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        assertThatThrownBy(() -> validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解第一页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s001"]},
            {"type":"question","role":"teacher","prompt":"问题？","expected":["参考"],"keywords":["B 树"],
             "sourceChunkIds":["c1"],"questionSource":"model","slideRefs":["deck-a-s001"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["结点"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]},
            {"type":"explain","role":"teacher","content":"教材延伸","keywords":["延伸"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]}]}
            """, rules(spine, 12)))
            .hasMessageContaining("请删除 slideRefs 字段");
    }

    @Test
    void anExtensionStepInheritsTheRangeOfTheLastPage() throws Exception {
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        ArrayNode steps = validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解第一页","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s001"]},
            {"type":"question","role":"teacher","prompt":"问题？","expected":["参考"],"keywords":["B 树"],
             "sourceChunkIds":["c1"],"questionSource":"model","slideRefs":["deck-a-s001"]},
            {"type":"explain","role":"teacher","content":"讲解第二页","keywords":["结点"],"sourceChunkIds":["c2"],
             "slideRefs":["deck-a-s002"]},
            {"type":"explain","role":"teacher","content":"教材延伸","keywords":["延伸"],"sourceChunkIds":["c2"]}]}
            """, rules(spine, 12));

        ObjectNode extension = (ObjectNode) steps.get(3);
        assertThat(extension.has("slideRefs")).isFalse();
        assertThat(extension.path("slideScope").path("subLessonId").asText()).isEqualTo("08-04B");
        assertThat(extension.path("textbookMatch").asText()).isEqualTo("extension");
    }

    @Test
    void withoutASpineTheRangeComesFromThePageTheStepChose() throws Exception {
        ArrayNode steps = validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideScope":{"subLessonId":"瞎写的","scene":"瞎写的"}},
            {"type":"explain","role":"teacher","content":"讲解","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s002"]},
            {"type":"question","role":"teacher","prompt":"问题？","expected":["参考"],"keywords":["B 树"],
             "sourceChunkIds":["c1"],"questionSource":"model"},
            {"type":"summary","role":"teacher","content":"总结","keywords":["B 树"],"sourceChunkIds":["c2"]}]}
            """, rulesWithoutSpine());

        // A step that names no page teaches over the page already on screen, so it declares no range of its own.
        assertThat(steps.get(0).has("slideScope")).isFalse();
        assertThat(steps.get(0).path("evidence").asText()).contains("B 树的查找效率与树高有关");
        assertThat(steps.get(0).path("sourcePages").get(0).asText()).isEqualTo("第 320 页");
        assertThat(steps.get(1).path("slideScope").path("subLessonId").asText()).isEqualTo("08-04B");
        assertThat(steps.get(1).path("slideScope").path("scene").asText()).isEqualTo("practice");
        assertThat(steps.get(3).has("slideScope")).isFalse();
    }

    @Test
    void aPageThisLessonDoesNotHaveIsRefusedWithThePagesThatDoExist() throws Exception {
        assertThatThrownBy(() -> validate("""
            {"lessonId":"lesson-1","title":"B 树查找","steps":[
            {"type":"explain","role":"teacher","content":"讲解","keywords":["B 树"],"sourceChunkIds":["c1"],
             "slideRefs":["deck-a-s009"]},
            {"type":"explain","role":"teacher","content":"讲解","keywords":["B 树"],"sourceChunkIds":["c1"]},
            {"type":"explain","role":"teacher","content":"讲解","keywords":["B 树"],"sourceChunkIds":["c1"]}]}
            """, rulesWithoutSpine()))
            .hasMessageContaining("本课时不存在的课件页")
            .hasMessageContaining("deck-a-s001");
    }

    @Test
    void theLocalSpineTeachesTheSamePagesInTheSameOrder() {
        // The fallback used when a model answer is refused: the pages come from the deck itself, so the
        // lesson still walks every page in order and still carries the range of the page it shows.
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        List<Map<String, Object>> rows = rowsWithWorkedExample();

        List<ObjectNode> steps = SlideSpinePlan.steps(mapper, spine, LessonPassageIndex.of(rows), true);
        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).path("slideRefs").get(0).asText()).isEqualTo("deck-a-s001");
        assertThat(steps.get(0).path("slideScope").path("subLessonId").asText()).isEqualTo("08-04A");
        assertThat(steps.get(1).path("slideRefs").get(0).asText()).isEqualTo("deck-a-s002");
        assertThat(steps.get(2).has("slideRefs")).isFalse();
        assertThat(steps.get(2).path("textbookMatch").asText()).isEqualTo("extension");
        assertThat(steps.get(2).path("sourcePages").get(0).asText()).isEqualTo("第 321 页");

        // A part that is not the lesson's last one ends where its pages end.
        assertThat(SlideSpinePlan.steps(mapper, spine, LessonPassageIndex.of(rows), false)).hasSize(2);
    }

    @Test
    void onlyARejectedAnswerFallsBackToTheLocalSpine() throws Exception {
        // The fallback exists so a model that keeps getting one part wrong cannot cost the learner the whole
        // lesson. It must not swallow anything else: an exhausted quota or a provider outage has to surface,
        // otherwise the author sees a lesson taught from the deck and never learns why.
        List<SlideSpinePlan.Slide> part = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        LessonPassageIndex textbook = LessonPassageIndex.of(rowsWithWorkedExample());

        ApiException rejected = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
            ClassroomModelJson.REJECTED_ANSWER, "模型修正后仍未通过校验：$.steps[6] …");
        assertThat(ClassroomPreparation.fallbackSteps(rejected, mapper, part, textbook, true))
            .as("a rejected answer is taught from the deck")
            .hasSize(3);
        assertThat(ClassroomPreparation.fallbackSteps(rejected, mapper, part, textbook, false))
            .as("only the last part appends textbook extensions")
            .hasSize(2);
        assertThat(ClassroomPreparation.fallbackSteps(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
            "AI_QUOTA_EXHAUSTED", "今日额度已用完"), mapper, part, textbook, true))
            .as("a quota problem is not a lesson to be improvised")
            .isEmpty();
        assertThat(ClassroomPreparation.fallbackSteps(rejected, mapper, List.of(), textbook, true))
            .as("a lesson without courseware has no local spine")
            .isEmpty();

        // What the fallback writes is exactly what a deterministic lesson would carry, so a part taught this
        // way is indistinguishable to the classroom from one the model wrote - and it passes the contract the
        // classroom reads when it opens the session.
        List<ObjectNode> steps = ClassroomPreparation.fallbackSteps(rejected, mapper, part, textbook, true);
        assertThat(steps.get(0).path("slideRefs").get(0).asText()).isEqualTo("deck-a-s001");
        assertThat(steps.get(0).path("slideScope").path("subLessonId").asText()).isEqualTo("08-04A");
        assertThat(steps.get(2).has("slideRefs")).isFalse();
        ObjectNode plan = mapper.createObjectNode();
        plan.put("lessonId", "lesson-1");
        plan.put("title", "B 树查找");
        ArrayNode declared = plan.putArray("steps");
        steps.forEach(declared::add);
        assertThat(new ClassroomScriptParser(mapper).parse(plan.toString()).lessonId()).isEqualTo("lesson-1");
    }

    @Test
    void theLocalSpineSatisfiesTheScriptContractEvenWithoutQuestions() throws Exception {
        // The two paths must agree on the contract the classroom reads. The script parser accepts a lesson
        // that teaches without asking (a draft has no questions yet), while the per-part rules - which are
        // about what one model answer must do - still want a question, which is exactly why a refused part
        // is merged from the local spine instead of being sent back through the validator.
        List<SlideSpinePlan.Slide> spine = List.of(
            page("deck-a-s001", 1, "8.3.7 B 树查找", "08-04A", "concept"),
            page("deck-a-s002", 2, "8.3.7 B 树查找示例", "08-04B", "practice"));
        List<Map<String, Object>> chunks = rowsWithWorkedExample();
        List<ObjectNode> localSteps = SlideSpinePlan.steps(mapper, spine, LessonPassageIndex.of(chunks), true);
        ObjectNode plan = mapper.createObjectNode();
        plan.put("lessonId", "lesson-1");
        plan.put("title", "B 树查找");
        ArrayNode steps = plan.putArray("steps");
        localSteps.forEach(steps::add);

        assertThat(parser.parse(plan.toString()).stage(ClassroomState.EXPLAIN).path("content").asText()).isNotEmpty();
        assertThatThrownBy(() -> ClassroomPreparation.validatePart(plan, rules(spine, 12), parser, mapper))
            .hasMessageContaining("至少 1 个 type=question");
    }
}
