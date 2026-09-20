package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import com.feng.dsagent.presentation.PresentationSlide;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The scene a step declares must describe the page on screen, so it is derived from that page alone. */
class ClassroomPreparationSceneTest {

    private static PresentationSlide slide(String title, String role, String teachingRole) {
        return new PresentationSlide("s1", "deck", "deck title", 1, "01", title, "", "", "",
            teachingRole, "", List.of(), List.of(), true, List.of("01-01A"), "", "2.3.1", role, List.of());
    }

    @Test
    void reviewPagesAreIntroWhateverThePlanCalledThem() {
        // The regression this guards: a neighbouring deck's review page was filed under a summary scene
        // and the narration became a lesson wrap-up over a page that only lists what was learned before.
        assertThat(ClassroomPreparation.sceneOf(slide("上次课回顾", "内容", "title"))).isEqualTo("intro");
        assertThat(ClassroomPreparation.sceneOf(slide("复习：线性表", "", ""))).isEqualTo("intro");
    }

    @Test
    void closingExerciseAndConceptPagesMapToTheirOwnScenes() {
        assertThat(ClassroomPreparation.sceneOf(slide("链表小结", "小结", ""))).isEqualTo("summary");
        assertThat(ClassroomPreparation.sceneOf(slide("第二章总结", "", "summary"))).isEqualTo("summary");
        assertThat(ClassroomPreparation.sceneOf(slide("谢谢大家！", "", ""))).isEqualTo("summary");
        assertThat(ClassroomPreparation.sceneOf(slide("课堂练习：链表逆置", "练习", "example"))).isEqualTo("practice");
        assertThat(ClassroomPreparation.sceneOf(slide("2.3.1 单链表", "定义", "definition"))).isEqualTo("concept");
        assertThat(ClassroomPreparation.sceneOf(slide("单链表的删除", "算法", "algorithm"))).isEqualTo("concept");
    }

    /** A page the deck itself set a task on, so a question asked there is the courseware's question. */
    private static SlideSpinePlan.Slide spine(String title) {
        return new SlideSpinePlan.Slide("s1", 1, title, "", "", "", "", "02-03A", "", "practice", List.of());
    }

    @Test
    void onlyPagesThatCarryTheirOwnTaskCountAsCoursewareQuestions() {
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("课堂练习：链表逆置 给定一个单链表 将其逆置为 ,"))).isTrue();
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("测试"))).isTrue();
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("例题2.3 求两个集合的差"))).isTrue();
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("习题 2.4"))).isTrue();
        // An algorithm page the deck happened to file as a worked example asks nothing of the learner:
        // forcing "slide" there would label the teacher's own question as the courseware's.
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("求单链表的长度算法："))).isFalse();
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("8.3.4 二叉搜索树的节点删除"))).isFalse();
        assertThat(ClassroomPreparation.pagePosesItsOwnQuestion(spine("思考与讨论"))).isFalse();
    }
}
