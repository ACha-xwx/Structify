package com.feng.dsagent.studyroom.fragment;

import java.util.List;

public record FragmentPlan(String key, int order, String objective, String activity,
        List<String> coverageKeys, List<String> requiredKinds, boolean highRisk) {
    public FragmentPlan {
        coverageKeys = List.copyOf(coverageKeys);
        requiredKinds = List.copyOf(requiredKinds);
    }
}
