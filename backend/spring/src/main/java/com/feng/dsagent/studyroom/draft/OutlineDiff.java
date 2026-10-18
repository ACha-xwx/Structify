package com.feng.dsagent.studyroom.draft;

import java.util.*;

public final class OutlineDiff {
    private OutlineDiff() {}

    public record Change(String unitId, String kind, String summary) {}

    public static List<Change> between(ReviewedOutline before, ReviewedOutline after) {
        var changes = new ArrayList<Change>();
        if (before == null)
            return after.units().stream()
                    .map(u -> new Change(u.id(), "ADDED", "新增：" + u.title()))
                    .toList();
        Map<String, ReviewedOutline.Unit> old = new LinkedHashMap<>();
        before.units().forEach(u -> old.put(u.id(), u));
        var ids = after.units().stream().map(ReviewedOutline.Unit::id).toList();
        for (int i = 0; i < after.units().size(); i++) {
            var unit = after.units().get(i);
            var previous = old.get(unit.id());
            if (previous == null) changes.add(new Change(unit.id(), "ADDED", "新增：" + unit.title()));
            else if (!previous.equals(unit))
                changes.add(new Change(unit.id(), "CHANGED", "调整目标、覆盖或材料：" + unit.title()));
            else if (before.units().indexOf(previous) != i)
                changes.add(new Change(unit.id(), "MOVED", "调整顺序：" + unit.title()));
        }
        before.units().stream()
                .filter(u -> !ids.contains(u.id()))
                .forEach(u -> changes.add(new Change(u.id(), "REMOVED", "移除：" + u.title())));
        if (before.depth() != after.depth())
            changes.add(new Change(null, "CHANGED", "深度调整为 " + after.depth()));
        if (!before.exclusions().equals(after.exclusions())
                || !before.excludedCoverageKeys().equals(after.excludedCoverageKeys()))
            changes.add(new Change(null, "CHANGED", "调整本次不覆盖的内容"));
        return List.copyOf(changes);
    }
}
