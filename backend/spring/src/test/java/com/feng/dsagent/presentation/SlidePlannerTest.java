package com.feng.dsagent.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The decks follow the same sub-lesson split as the reviewed textbook (08-02A 查找与插入 versus
 * 08-02B 删除与性能) but rarely hold a page for every textbook example. These cases pin the behaviour
 * that keeps the classroom pane on lecture: split the steps by sub-lesson, advance by scene, keep the
 * current page when the deck has nothing, and never jump to an unrelated part of the deck.
 */
class SlidePlannerTest {

    private static final Map<String, PresentationSlide> SLIDES = Map.of(
        "a1", slide("a1", "8.3.1 定义", "二叉排序树的定义与性质", List.of("二叉排序树", "定义", "左子树")),
        "a2", slide("a2", "8.3.2 查找算法", "二叉排序树的查找过程", List.of("查找", "比较", "结点")),
        "a3", slide("a3", "示例", "插入序列建立二叉排序树", List.of("示例", "插入", "序列")),
        "b1", slide("b1", "8.3.4 节点删除", "删除结点的三种情况", List.of("删除", "结点", "情况")),
        "b2", slide("b2", "8.3.5 性能分析", "平均查找长度与形态", List.of("性能", "平均查找长度"))
    );

    private static PresentationSlide slide(String id, String title, String summary, List<String> concepts) {
        return new PresentationSlide(id, "deck", "第八章-查找01", 1, "08", title, "", "", summary, "concept", "", concepts, List.of(), true, List.of(), "/image", "8.3", "定义", concepts);
    }

    private static PresentationCatalog.SubLessonPlan plan(String id, String title, String... scenes) {
        List<PresentationCatalog.ScenePlan> plans = new java.util.ArrayList<>();
        for (String scene : scenes) {
            String[] parts = scene.split("=", 2);
            plans.add(new PresentationCatalog.ScenePlan(parts[0], "direct", 0.5, List.of(parts[1].split(","))));
        }
        return new PresentationCatalog.SubLessonPlan(id, title, plans);
    }

    private static final List<PresentationCatalog.SubLessonPlan> PLAN = List.of(
        plan("08-02A", "二叉排序树的查找与插入", "intro=a1", "concept-one=a1,a2", "practice=a3"),
        plan("08-02B", "二叉排序树的删除与性能", "concept-one=b1", "summary=b2")
    );

    private static final Function<String, PresentationSlide> LOOKUP = SLIDES::get;

    private static SlidePlanner.StepInput step(String text) {
        return new SlidePlanner.StepInput(text, List.of());
    }

    @Test
    void splitsTheLectureBySubLessonAndWalksTheScenesInOrder() {
        List<SlidePlanner.StepPlan> plans = SlidePlanner.plan(List.of(
            step("二叉排序树的定义：左子树上的值均小于根结点，右子树上的值均大于根结点"),
            step("在二叉排序树上查找关键字，从根开始比较"),
            step("例 8.2 给出插入序列 45,24,53 建立二叉排序树的过程"),
            step("从二叉排序树中删除一个结点，要分三种情况处理"),
            step("删除之后分析二叉排序树的平均查找长度与树的形态")
        ), PLAN, LOOKUP);

        assertThat(plans).hasSize(5);
        assertThat(plans.get(0).subLessonId()).isEqualTo("08-02A");
        assertThat(plans.get(0).slideId()).isEqualTo("a1");
        assertThat(plans.get(0).kind()).isEqualTo(SlidePlanner.Kind.DIRECT);
        assertThat(plans.get(1).slideId()).isEqualTo("a2");
        assertThat(plans.get(2).slideId()).isEqualTo("a3");
        assertThat(plans.get(3).subLessonId()).isEqualTo("08-02B");
        assertThat(plans.get(3).slideId()).isEqualTo("b1");
        assertThat(plans.get(4).slideId()).isEqualTo("b2");
    }

    @Test
    void keepsThePaneWhereTheLectureIsEvenIfALaterParagraphSoundsLikeAnEarlierTopic() {
        List<SlidePlanner.StepPlan> plans = SlidePlanner.plan(List.of(
            step("删除结点的三种情况"),
            step("二叉排序树的定义与性质"),
            step("性能分析：平均查找长度")
        ), PLAN, LOOKUP);

        // The lecture has already moved on to 08-02B; a paragraph that sounds like the definition must not
        // pull the deck back to the beginning of the lesson.
        assertThat(plans).allMatch(plan -> "08-02B".equals(plan.subLessonId()));
        assertThat(plans).extracting(SlidePlanner.StepPlan::slideId).containsExactly("b1", "b1", "b2");
    }

    @Test
    void keepsTheCurrentPageWhenTheDeckHasNoPageForTheExample() {
        List<SlidePlanner.StepPlan> plans = SlidePlanner.plan(List.of(
            step("二叉排序树的定义"),
            step("图 8.8 给出删除结点时四种旋转调整的示意图，请对照观察指针变化"),
            step("下面是本章的小结")
        ), PLAN, LOOKUP);

        assertThat(plans.get(1).slideId()).isEqualTo(plans.get(0).slideId());
        assertThat(plans.get(1).kind()).isEqualTo(SlidePlanner.Kind.CONTINUITY);
    }

    @Test
    void reportsNoCoursewareWhenTheLessonHasNoDeck() {
        List<SlidePlanner.StepPlan> plans = SlidePlanner.plan(List.of(step("串的模式匹配")), List.of(), LOOKUP);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).kind()).isEqualTo(SlidePlanner.Kind.NONE);
        assertThat(plans.get(0).slideId()).isNull();
    }
}
