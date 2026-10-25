package com.feng.dsagent.studyroom.draft;

import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.studyroom.draft.service.OutlineFeasibilityService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.curriculum.CurriculumCatalog;

import org.junit.jupiter.api.Test;

import java.util.*;

class OutlineFeasibilityTest {
    final StudyroomResourceCatalog catalog = mock(StudyroomResourceCatalog.class);
    final OutlineFeasibilityService service =
            new OutlineFeasibilityService(
                    catalog,
                    new CurriculumCatalog(),
                    mock(com.feng.dsagent.compiler.CodeExecutionReadiness.class));

    ReviewedOutline outline(
            List<String> coverage, List<String> activities, List<String> exclusions) {
        return new ReviewedOutline(
                1,
                LearningDepth.STANDARD,
                "栈",
                List.of("理解栈"),
                List.of(),
                List.of(
                        new ReviewedOutline.Unit(
                                "stack",
                                "栈实现",
                                "理解顺序栈实现",
                                coverage,
                                "EXPLAIN",
                                activities,
                                List.of())),
                exclusions,
                List.of(),
                List.of(),
                null,
                "EVIDENCE_ONLY");
    }

    @Test
    void cannotCallASlideOnlyOverviewAnImplementationLesson() {
        when(catalog.candidates(null)).thenReturn(List.of());
        var result =
                service.assess(
                        outline(List.of("stack.implementation"), List.of("CONCEPT"), List.of()),
                        "栈实现",
                        null);
        assertThat(result.issues())
                .anyMatch(
                        i ->
                                i.code().equals("IMPLEMENTATION_ACTIVITY_MISSING")
                                        && i.severity().equals("BLOCKER"));
    }

    @Test
    void matchingCodeBecomesAnExplicitReviewedCommitment() {
        when(catalog.candidates(null))
                .thenReturn(
                        List.of(
                                new StudyroomResourceCatalog.Candidate(
                                        StudyroomModelOutput.ResourceType.C_CODE,
                                        "stack-c",
                                        "栈实现",
                                        "顺序栈入栈出栈",
                                        List.of())));
        var result =
                service.assess(
                        outline(
                                List.of("stack.implementation"),
                                List.of("IMPLEMENTATION"),
                                List.of()),
                        "栈实现",
                        null);
        assertThat(result.outline().units().getFirst().materials())
                .anyMatch(
                        m ->
                                m.kind().equals("C_CODE")
                                        && m.requirement().equals("REQUIRED")
                                        && m.candidateIds().contains("stack-c"));
    }

    @Test
    void broadLearningNeedsCoverageOrExplicitExclusions() {
        when(catalog.candidates(null)).thenReturn(List.of());
        var result =
                service.assess(
                        outline(List.of("stack.definition"), List.of("CONCEPT"), List.of()),
                        "栈的知识有哪些",
                        null);
        assertThat(result.issues()).anyMatch(i -> i.code().equals("COVERAGE_GAP"));
        var narrowed =
                service.assess(
                        outline(
                                List.of("stack.definition"),
                                List.of("CONCEPT"),
                                List.of(
                                        "stack.operations",
                                        "stack.storage",
                                        "stack.implementation")),
                        "栈的知识有哪些",
                        null);
        assertThat(narrowed.issues()).noneMatch(i -> i.code().equals("COVERAGE_GAP"));
    }

    @Test
    void aSpecificApplicationsQuestionDoesNotRequireTheEntireStackCurriculum() {
        when(catalog.candidates(null)).thenReturn(List.of());
        var result =
                service.assess(
                        outline(List.of("stack.definition"), List.of("CONCEPT"), List.of()),
                        "栈有哪些应用",
                        null);
        assertThat(result.issues()).noneMatch(issue -> issue.code().equals("COVERAGE_GAP"));
    }
}
