package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The courseware spine: one teaching step per page, textbook depth selected by the page's own section,
 * and textbook material without a page kept as an extension that leaves the screen where it is.
 */
class SlideSpinePlanTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static Map<String, Object> page(int number, String content) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "chunk-" + number);
        row.put("page_label", "第 " + number + " 页");
        row.put("content", "### 教材页 " + number + "（PDF页 " + (number + 11) + "）\n> OCR质量：已核验\n" + content);
        return row;
    }

    /** Rows arrive from the database in hash order, so the index has to restore page order itself. */
    private static LessonPassageIndex textbook() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
            page(276, "8.3.4\u3000二叉搜索树的节点删除\n删除结点分三种情况：叶子结点直接删除；只有一个孩子时用该孩子顶替；两个孩子时用中序前驱替代。"),
            page(278, "8.3.5\u3000二叉排序树的性能分析\n查找长度与树的形态有关，最坏情况退化为单支树。"),
            page(271, "基于树的查找法是将待查表组织成特定树的形式并在树结构上实现查找的方法。"),
            page(272, "8.3.1\u3000二叉排序树\n二叉排序树或者是一棵空树，或者是具有左子树小于根结点、右子树大于根结点性质的树。\n例 8.2 设关键字的输入顺序为 45，24，53，给出生成的二叉排序树。"),
            page(274, "8.3.2\u3000二叉搜索树中的查找算法\n查找从根结点出发，沿一条路径走到待查结点。")
        ));
        return LessonPassageIndex.of(rows);
    }

    private static SlideSpinePlan.Slide slide(String id, int number, String title, String section,
                                              String subLessonId, String scene, String role, String body) {
        return new SlideSpinePlan.Slide(id, number, title, body, title + "摘要", role, section,
            subLessonId, subLessonId + " " + title, scene, List.of("二叉排序树", title));
    }

    private static List<SlideSpinePlan.Slide> deck() {
        return List.of(
            slide("deck-s01", 1, "二叉排序树的定义", "8.3.1", "08-02A", "concept-one", "定义",
                "二叉排序树的性质：左子树小于根结点，右子树大于根结点。"),
            slide("deck-s02", 2, "二叉搜索树的节点删除", "8.3.4", "08-02B", "concept-two", "操作",
                "删除结点时不能把整棵子树删掉。")
        );
    }

    @Test
    void restoresPageOrderAndCarriesSectionsForward() {
        List<LessonPassageIndex.Passage> passages = textbook().passages();
        assertThat(passages).extracting(LessonPassageIndex.Passage::page).containsExactly(271, 272, 274, 276, 278);
        // The lesson opens with a page that carries no section line, so it inherits the empty section.
        assertThat(passages.get(0).section()).isEmpty();
        assertThat(passages.get(1).sectionsOnPage()).containsExactly("8.3.1");
        // A page introducing its own section still records that section.
        assertThat(passages.get(3).sectionsOnPage()).containsExactly("8.3.4");
    }

    @Test
    void selectsTheDeclaredSectionFirstAndWidensOnlyWhenNeeded() {
        LessonPassageIndex textbook = textbook();
        LessonPassageIndex.Evidence exact = textbook.evidence("8.3.4", "结点删除的三种情况", 2);
        assertThat(exact.level()).isEqualTo(LessonPassageIndex.Level.EXACT);
        assertThat(exact.passages()).extracting(LessonPassageIndex.Passage::page).contains(276);

        // 8.3.9 does not exist; the parent section 8.3 does, so the whole of 8.3 backs the page.
        LessonPassageIndex.Evidence widened = textbook.evidence("8.3.9", "二叉排序树", 3);
        assertThat(widened.level()).isEqualTo(LessonPassageIndex.Level.PARENT);
        assertThat(widened.scope()).isEqualTo("8.3");

        LessonPassageIndex.Evidence lessonWide = textbook.evidence("", "二叉排序树", 2);
        assertThat(lessonWide.level()).isEqualTo(LessonPassageIndex.Level.CHAPTER);
        assertThat(lessonWide.passages()).isNotEmpty();
    }

    @Test
    void ranksPassagesByHowMuchOfThePageTheyExplain() {
        LessonPassageIndex.Evidence evidence = textbook().evidence("8.3.4", "删除 叶子结点 中序前驱 替代", 1);
        assertThat(evidence.passages()).singleElement()
            .satisfies(passage -> assertThat(passage.text()).contains("中序前驱"));
    }

    @Test
    void buildsOneStepPerPageInDeckOrder() {
        JsonNode plan = SlideSpinePlan.build(mapper, "textbook-abc", "查找-二叉排序树（已核验教材第 271–278 页选段）",
            "第 271–278 页", deck(), textbook()).plan();

        assertThat(plan.path("lessonId").asText()).isEqualTo("textbook-abc");
        assertThat(plan.path("title").asText()).isEqualTo("查找-二叉排序树");
        assertThat(plan.path("spine").asText()).isEqualTo("courseware");

        JsonNode steps = plan.path("steps");
        // Steps that carry a page must appear in deck order, whatever else was inserted around them.
        List<JsonNode> pageSteps = new ArrayList<>();
        steps.forEach(step -> {
            if (step.path("slideRefs").isArray() && !step.path("slideRefs").isEmpty()) pageSteps.add(step);
        });
        assertThat(pageSteps).hasSize(2);
        for (int index = 0; index < 2; index++) {
            JsonNode step = pageSteps.get(index);
            assertThat(step.path("slideRefs").get(0).asText()).isEqualTo(deck().get(index).id());
            assertThat(step.path("slideScope").path("subLessonId").asText()).isEqualTo(deck().get(index).subLessonId());
            assertThat(step.path("content").asText()).isNotBlank();
            assertThat(step.path("sourceChunkIds")).isNotEmpty();
            assertThat(step.path("sourcePages")).isNotEmpty();
        }
        // The deletion page declares 8.3.4, whose only textbook page is 276.
        assertThat(pageSteps.get(1).path("textbookMatch").asText()).isEqualTo("exact");
        assertThat(pageSteps.get(1).path("sourcePages").get(0).asText()).isEqualTo("第 276 页");
    }

    @Test
    void keepsTextbookMaterialWithoutAPageAsAnExtensionThatLeavesTheScreenAlone() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
            page(272, "8.3.1\u3000二叉排序树\n二叉排序树的性质：左小右大。"),
            page(282, "8.6\u3000习题课\n例 8.9 已知一棵二叉排序树，请写出删除某结点后的形态。")
        ));
        List<SlideSpinePlan.Slide> deck = List.of(
            slide("deck-s01", 1, "二叉排序树的定义", "8.3.1", "08-02A", "concept-one", "定义", "左小右大。")
        );

        JsonNode steps = SlideSpinePlan.build(mapper, "textbook-abc", "查找-二叉排序树", "第 271–282 页",
            deck, LessonPassageIndex.of(rows)).plan().path("steps");

        assertThat(steps.size()).isEqualTo(2);
        JsonNode extension = steps.get(1);
        assertThat(extension.path("textbookMatch").asText()).isEqualTo("extension");
        // No page of its own: the classroom keeps the page already on screen.
        assertThat(extension.path("slideRefs").isMissingNode()).isTrue();
        // ...but it still belongs to the range that was on screen, which is what says so.
        assertThat(extension.path("slideScope").path("subLessonId").asText()).isEqualTo("08-02A");
        assertThat(extension.path("sourcePages").get(0).asText()).isEqualTo("第 282 页");
    }

    @Test
    void leavesTheTextbookExampleOutWhenTheDeckAlreadyHasAnExercisePage() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
            page(272, "8.3.1\u3000二叉排序树\n例 8.2 设关键字的输入顺序为 45，24，53，给出生成的二叉排序树。")
        ));
        List<SlideSpinePlan.Slide> withExamplePage = List.of(
            slide("deck-s01", 1, "二叉排序树的定义", "8.3.1", "08-02A", "concept-one", "定义", "左小右大。"),
            slide("deck-s02", 2, "二叉排序树的插入示例", "8.3.1", "08-02A", "example", "示例", "逐个插入建立树。")
        );

        JsonNode steps = SlideSpinePlan.build(mapper, "textbook-abc", "查找-二叉排序树", "第 272 页",
            withExamplePage, LessonPassageIndex.of(rows)).plan().path("steps");

        // The deck practises the section itself, so the textbook example is not repeated as an extension.
        assertThat(steps.size()).isEqualTo(2);
        assertThat(steps).allSatisfy(step -> assertThat(step.path("slideRefs")).isNotEmpty());
    }

    @Test
    void producesAScriptTheClassroomContractAccepts() {
        String json = SlideSpinePlan.build(mapper, "textbook-abc", "查找-二叉排序树", "第 271–278 页",
            deck(), textbook()).plan().toString();

        // The locally built spine must satisfy the very same contract a model answer has to satisfy.
        ClassroomScriptPlan parsed = new ClassroomScriptParser(mapper).parse(json);
        assertThat(parsed.lessonId()).isEqualTo("textbook-abc");
        assertThat(parsed.stage(ClassroomState.EXPLAIN).path("content").asText()).isNotBlank();
    }
}
