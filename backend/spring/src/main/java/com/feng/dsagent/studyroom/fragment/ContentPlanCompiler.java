package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Frozen activity templates, not a second model-generated lesson outline. */
@Component
public final class ContentPlanCompiler {
    public List<FragmentPlan> compile(LearningDepth depth, ReviewedOutline.Unit unit) {
        List<FragmentPlan> plans = new ArrayList<>();
        for (String activity : new LinkedHashSet<>(unit.activities())) {
            List<String> targets = activity.equals("PROCESS") && unit.coverageKeys().contains("stack.operations")
                    ? List.of("stack.push", "stack.pop") : List.of(String.join("+", unit.coverageKeys()));
            for (String target : targets) {
                String detail = switch (target) {
                    case "stack.push" -> "观察入栈前后栈顶与元素的变化";
                    case "stack.pop" -> "观察出栈前后栈顶与元素的变化";
                    default -> switch (activity) {
                        case "CONCEPT" -> "解释概念与关键关系";
                        case "PROCESS" -> "演示过程及状态变化";
                        case "IMPLEMENTATION" -> "结合真实 C 源码解释实现";
                        case "EXPERIMENT" -> "运行已确认的实验并观察结果";
                        case "CHECK" -> "检查当前知识点的理解";
                        default -> throw new IllegalArgumentException("Unknown activity: " + activity);
                    };
                };
                var required = unit.materials().stream()
                    .filter(m -> "REQUIRED".equals(m.requirement()))
                    .map(ReviewedOutline.Material::kind)
                    .filter(kind -> switch (kind) {
                        case "C_CODE" -> Set.of("IMPLEMENTATION", "EXPERIMENT").contains(activity);
                        case "ANIMATION" -> Set.of("PROCESS", "EXPERIMENT").contains(activity);
                        case "PPT" -> activity.equals("CONCEPT") || unit.activities().size() == 1;
                        default -> false;
                    }).distinct().toList();
                plans.add(new FragmentPlan("f-" + OutlineJson.hash(unit.id() + ":" + activity + ":" + target).substring(0, 24),
                    plans.size(), unit.title() + "：" + unit.objective() + "；" + detail,
                    activity, unit.coverageKeys(), required,
                    !activity.equals("CONCEPT") && !activity.equals("CHECK")));
            }
        }
        int limit = switch (depth) { case QUICK -> 3; case STANDARD -> 6; case DEEP -> 8; };
        if (plans.isEmpty() || plans.size() > limit)
            throw new IllegalArgumentException("OUTLINE_FRAGMENT_LIMIT: 请缩小知识点范围或调整详细程度");
        Set<String> satisfied = new HashSet<>();
        plans.forEach(p -> satisfied.addAll(p.requiredKinds()));
        if (unit.materials().stream().anyMatch(m -> m.requirement().equals("REQUIRED") && !satisfied.contains(m.kind())))
            throw new IllegalArgumentException("OUTLINE_REQUIRED_ACTIVITY_MISSING: 必需材料没有对应教学活动");
        return List.copyOf(plans);
    }

    public Map<String, List<FragmentPlan>> preview(ReviewedOutline outline) {
        Map<String, List<FragmentPlan>> result = new LinkedHashMap<>();
        outline.units().forEach(unit -> result.put(unit.id(), compile(outline.depth(), unit)));
        return Collections.unmodifiableMap(result);
    }

    public String hash(ReviewedOutline outline, OutlineJson json) {
        return OutlineJson.hash(json.write(List.of("fragment-plan-1", outline, preview(outline))));
    }
}
