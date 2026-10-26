package com.feng.dsagent.studyroom.draft.domain;

import java.util.List;

public record ReviewedOutline(
        int schemaVersion,
        LearningDepth depth,
        String title,
        List<String> objectives,
        List<String> prerequisites,
        List<Unit> units,
        List<String> excludedCoverageKeys,
        List<String> exclusions,
        List<String> evidenceIds,
        String curriculumVersion,
        String coverageAssessment) {
    public ReviewedOutline {
        objectives = copy(objectives);
        prerequisites = copy(prerequisites);
        units = copy(units);
        excludedCoverageKeys = copy(excludedCoverageKeys);
        exclusions = copy(exclusions);
        evidenceIds = copy(evidenceIds);
    }

    public record Unit(
            String id,
            String title,
            String objective,
            List<String> coverageKeys,
            String expansion,
            List<String> activities,
            List<Material> materials) {
        public Unit {
            coverageKeys = copy(coverageKeys);
            activities = copy(activities);
            materials = copy(materials);
        }
    }

    public record Material(
            String kind,
            String requirement,
            String purpose,
            List<String> candidateIds,
            String availability) {
        public Material {
            candidateIds = copy(candidateIds);
        }
    }

    public record Issue(String code, String unitId, String severity, String message) {}

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
