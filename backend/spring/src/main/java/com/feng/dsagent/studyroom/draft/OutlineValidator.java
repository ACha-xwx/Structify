package com.feng.dsagent.studyroom.draft;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OutlineValidator {
    public void validate(ReviewedOutline outline, LearningDepth depth) {
        if (outline.schemaVersion() != 1
                || outline.depth() != depth
                || outline.title() == null
                || outline.title().isBlank()
                || outline.title().length() > 160
                || outline.units().isEmpty()
                || outline.units().size() > 24
                || outline.objectives().isEmpty()
                || outline.objectives().size() > 12
                || outline.objectives().stream()
                        .anyMatch(v -> v == null || v.isBlank() || v.length() > 400))
            throw new IllegalArgumentException("大纲结构或深度不匹配");
        Set<String> ids = new HashSet<>();
        for (var unit : outline.units()) {
            if (unit.id() == null
                    || !unit.id().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")
                    || !ids.add(unit.id())
                    || unit.title() == null
                    || unit.title().isBlank()
                    || unit.title().length() > 160
                    || unit.objective() == null
                    || unit.objective().isBlank()
                    || unit.objective().length() > 400
                    || unit.coverageKeys().isEmpty()
                    || unit.activities().isEmpty()
                    || unit.activities().size() > 8
                    || unit.materials().size() > 9
                    || unit.coverageKeys().size() > 32
                    || !Set.of("INTRODUCE", "EXPLAIN", "PRACTICE").contains(unit.expansion())
                    || !Set.of("CONCEPT", "PROCESS", "IMPLEMENTATION", "EXPERIMENT", "CHECK")
                            .containsAll(unit.activities()))
                throw new IllegalArgumentException("单元目标、活动或标识不合法");
            if (unit.coverageKeys().stream().anyMatch(outline.excludedCoverageKeys()::contains))
                throw new IllegalArgumentException("大纲重新加入了已排除的知识点");
            for (String kind : List.of("PPT", "ANIMATION", "C_CODE"))
                if (unit.materials().stream().filter(m -> kind.equals(m.kind())).count() > 3)
                    throw new IllegalArgumentException("同种材料用途最多三项");
            for (var material : unit.materials()) {
                if (!Set.of("PPT", "ANIMATION", "C_CODE").contains(material.kind())
                        || !Set.of("REQUIRED", "RECOMMENDED", "OPTIONAL")
                                .contains(material.requirement())
                        || material.purpose() == null
                        || material.purpose().isBlank()
                        || material.purpose().length() > 200)
                    throw new IllegalArgumentException("材料类型或教学用途不合法");
            }
        }
    }
}
