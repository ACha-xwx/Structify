package com.feng.dsagent.studyroom;

import com.feng.dsagent.studyroom.draft.ReviewedOutline;

import java.util.*;

final class StudyroomUnitPlanPolicy {
    private StudyroomUnitPlanPolicy() {}

    static void validate(StudyroomLessonPlanner.Plan plan, ReviewedOutline.Unit unit) {
        var intents =
                plan.steps().stream()
                        .map(StudyroomLessonPlanner.Goal::intent)
                        .collect(java.util.stream.Collectors.toSet());
        var missing = new HashSet<>(unit.activities());
        missing.removeAll(intents);
        if (!missing.isEmpty()) throw new IllegalArgumentException("缺少已确认活动：" + missing);
        var allowed = new HashSet<>(unit.activities());
        allowed.add("CONCEPT");
        allowed.add("CHECK");
        var extra = new HashSet<>(intents);
        extra.removeAll(allowed);
        if (!extra.isEmpty()) throw new IllegalArgumentException("不能新增未确认的过程、代码或运行活动：" + extra);
    }
}
