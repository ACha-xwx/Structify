package com.feng.dsagent.studyroom;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

class StudyroomLessonPlannerTest {
    @Test void keepsOneRequestedExperimentInsteadOfAddingPreparatorySteps() {
        var plan = new StudyroomLessonPlanner.Plan(2, "标题", "摘要", java.util.List.of(), java.util.List.of(
            new StudyroomLessonPlanner.Goal("s1", "概念", "理解概念", "CONCEPT"), new StudyroomLessonPlanner.Goal("s2", "实验", "观察空树", "EXPERIMENT")));
        assertThat(StudyroomLessonPlanner.limitSupplement(plan, "请补充一个空树输入的实验步骤").steps())
            .extracting(StudyroomLessonPlanner.Goal::id).containsExactly("s2");
    }
    @Test void recognizesExplicitCodeActivityEvenWhenModelLabelsItConcept() {
        var json = """
            {"schemaVersion":2,"title":"先序遍历","summary":"递归","evidenceIds":[],"steps":[{"id":"s1","title":"解读函数","objective":"逐行解释PreOrder代码","intent":"CONCEPT"},{"id":"s2","title":"认识先序","objective":"理解根左右顺序","intent":"CONCEPT"}]}
            """;
        var plan = StudyroomLessonPlanner.parse(new ObjectMapper(), json, Set.of());
        assertThat(plan.steps()).extracting(StudyroomLessonPlanner.Goal::intent).containsExactly("IMPLEMENTATION", "CONCEPT");
    }
}
