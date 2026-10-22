package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.studyroom.draft.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentPlanCompilerTest {
    private final ContentPlanCompiler compiler = new ContentPlanCompiler();

    @Test void freezesOnlyApprovedActivitiesAndStableKeys() {
        var unit = unit(List.of("CONCEPT", "PROCESS", "CHECK"));
        var plans = compiler.compile(LearningDepth.STANDARD, unit);
        assertThat(compiler.compile(LearningDepth.STANDARD, unit)).isEqualTo(plans);
        assertThat(plans).extracting(FragmentPlan::activity).containsExactly("CONCEPT", "PROCESS", "CHECK");
        assertThat(plans).extracting(FragmentPlan::key).doesNotHaveDuplicates();
        assertThat(plans).extracting(FragmentPlan::order).containsExactly(0, 1, 2);
    }

    @Test void doesNotSilentlyDropRequiredActivitiesWhenQuickPlanIsTooLarge() {
        assertThatThrownBy(() -> compiler.compile(LearningDepth.QUICK,
            unit(List.of("CONCEPT", "PROCESS", "IMPLEMENTATION", "CHECK"))))
            .hasMessageContaining("OUTLINE_FRAGMENT_LIMIT");
    }

    @Test void conceptDoesNotInheritCodeRequiredForImplementation() {
        var unit = new ReviewedOutline.Unit("u", "栈", "理解栈", List.of("stack.definition"),
            "EXPLAIN", List.of("CONCEPT", "IMPLEMENTATION"), List.of(new ReviewedOutline.Material(
                "C_CODE", "REQUIRED", "阅读实现", List.of(), "AVAILABLE")));
        var plans = compiler.compile(LearningDepth.STANDARD, unit);
        assertThat(plans.getFirst().requiredKinds()).isEmpty();
        assertThat(plans.getLast().requiredKinds()).containsExactly("C_CODE");
        assertThat(plans.getLast().highRisk()).isTrue();
    }

    private ReviewedOutline.Unit unit(List<String> activities) {
        return new ReviewedOutline.Unit("stack", "栈", "理解栈的知识", List.of("stack.definition"),
            "EXPLAIN", activities, List.of());
    }
}
