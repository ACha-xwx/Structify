package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FragmentBudgetTest {
    @Test void allPhasesShareFiniteCallsTokensAndActiveTime() {
        var budget = new FragmentBudget(4, 20000, Duration.ofMinutes(8));
        assertThat(budget.permits(2, 12000, Duration.ofMinutes(1))).isTrue();
        assertThat(budget.permits(4, 12000, Duration.ofMinutes(1))).isFalse();
        assertThat(budget.permits(2, 20000, Duration.ofMinutes(1))).isFalse();
        assertThat(budget.permits(2, 12000, Duration.ofMinutes(8))).isFalse();
    }
    @Test void invalidBudgetsAndNegativeCountersAreRejected() {
        assertThatThrownBy(() -> new FragmentBudget(0, 10, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FragmentBudget(4, 10, Duration.ofMinutes(1))
            .permits(-1, 0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
