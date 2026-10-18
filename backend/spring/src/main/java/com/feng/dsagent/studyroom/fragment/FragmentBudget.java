package com.feng.dsagent.studyroom.fragment;

import java.time.Duration;

public record FragmentBudget(int maximumCalls, long maximumTokens, Duration maximumActiveTime) {
    public FragmentBudget {
        if (maximumCalls < 1 || maximumTokens < 1 || maximumActiveTime == null
                || maximumActiveTime.isNegative() || maximumActiveTime.isZero())
            throw new IllegalArgumentException("Fragment budget must be positive");
    }

    public boolean permits(int completedCalls, long chargedTokens, Duration elapsed) {
        if (completedCalls < 0 || chargedTokens < 0 || elapsed == null || elapsed.isNegative())
            throw new IllegalArgumentException("Fragment counters cannot be negative");
        return completedCalls < maximumCalls && chargedTokens < maximumTokens
                && elapsed.compareTo(maximumActiveTime) < 0;
    }
}
