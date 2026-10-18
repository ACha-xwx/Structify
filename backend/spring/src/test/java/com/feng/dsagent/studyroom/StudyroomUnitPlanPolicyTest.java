package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;

import com.feng.dsagent.studyroom.draft.ReviewedOutline;

import org.junit.jupiter.api.Test;

import java.util.*;

class StudyroomUnitPlanPolicyTest {
    StudyroomLessonPlanner.Plan plan(String... intents) {
        var goals = new ArrayList<StudyroomLessonPlanner.Goal>();
        for (int i = 0; i < intents.length; i++)
            goals.add(new StudyroomLessonPlanner.Goal("s" + i, "目标", "目标", intents[i]));
        return new StudyroomLessonPlanner.Plan(2, "单元", "目标", List.of(), goals);
    }

    ReviewedOutline.Unit unit() {
        return new ReviewedOutline.Unit(
                "u",
                "栈操作",
                "理解操作",
                List.of("stack.operations"),
                "EXPLAIN",
                List.of("PROCESS", "CHECK"),
                List.of());
    }

    @Test
    void allowsHelpfulIntroductionButKeepsCommittedProcessAndCheck() {
        assertThatCode(
                        () ->
                                StudyroomUnitPlanPolicy.validate(
                                        plan("CONCEPT", "PROCESS", "PROCESS", "CHECK"), unit()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> StudyroomUnitPlanPolicy.validate(plan("CONCEPT", "CHECK"), unit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROCESS");
    }

    @Test
    void cannotIntroduceAnUnapprovedExperiment() {
        assertThatThrownBy(
                        () ->
                                StudyroomUnitPlanPolicy.validate(
                                        plan("PROCESS", "CHECK", "EXPERIMENT"), unit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPERIMENT");
    }
}
