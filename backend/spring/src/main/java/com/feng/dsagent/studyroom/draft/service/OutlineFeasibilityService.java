package com.feng.dsagent.studyroom.draft.service;

import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.studyroom.curriculum.CurriculumCatalog;

import com.feng.dsagent.studyroom.draft.ReviewedOutline;

import com.feng.dsagent.studyroom.*;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OutlineFeasibilityService {
    private final StudyroomResourceCatalog catalog;
    private final com.feng.dsagent.compiler.CodeExecutionReadiness execution;
    private final com.feng.dsagent.studyroom.curriculum.CurriculumCatalog curriculum;

    public OutlineFeasibilityService(
            StudyroomResourceCatalog catalog,
            com.feng.dsagent.studyroom.curriculum.CurriculumCatalog curriculum,
            com.feng.dsagent.compiler.CodeExecutionReadiness execution) {
        this.catalog = catalog;
        this.curriculum = curriculum;
        this.execution = execution;
    }

    public record Assessment(ReviewedOutline outline, List<ReviewedOutline.Issue> issues) {}

    public Assessment assess(ReviewedOutline outline, String question, String chapter) {
        var available = catalog.candidates(chapter);
        var issues = new ArrayList<ReviewedOutline.Issue>();
        var units = new ArrayList<ReviewedOutline.Unit>();
        for (var unit : outline.units()) {
            var candidates =
                    StudyroomResourceSelector.select(
                            available, question, unit.title() + " " + unit.objective());
            var materials = new ArrayList<ReviewedOutline.Material>();
            if (unit.activities().contains("EXPERIMENT") && !execution.available())
                issues.add(
                        new ReviewedOutline.Issue(
                                "CODE_EXECUTION_UNAVAILABLE",
                                unit.id(),
                                "BLOCKER",
                                "代码运行服务未配置。可以改为代码阅读与讲解，或配置运行服务后再进行实验。"));
            boolean needsCode =
                    unit.activities().stream()
                            .anyMatch(a -> Set.of("IMPLEMENTATION", "EXPERIMENT").contains(a));
            if (unit.coverageKeys().stream().anyMatch(k -> k.endsWith(".implementation"))
                    && !needsCode)
                issues.add(
                        new ReviewedOutline.Issue(
                                "IMPLEMENTATION_ACTIVITY_MISSING",
                                unit.id(),
                                "BLOCKER",
                                "实现知识点需要代码讲解或实验活动，不能只展示课件"));
            if (unit.coverageKeys().stream()
                            .anyMatch(k -> k.endsWith(".operations") || k.equals("tree.traversal"))
                    && !unit.activities().contains("PROCESS"))
                issues.add(
                        new ReviewedOutline.Issue(
                                "PROCESS_ACTIVITY_MISSING",
                                unit.id(),
                                "BLOCKER",
                                "操作或遍历知识点需要过程讲解活动"));
            if (needsCode && unit.materials().stream().noneMatch(m -> "C_CODE".equals(m.kind())))
                materials.add(
                        new ReviewedOutline.Material(
                                "C_CODE",
                                "REQUIRED",
                                "讲解实现或完成实验",
                                candidates.stream()
                                        .filter(
                                                c ->
                                                        c.type()
                                                                == StudyroomModelOutput.ResourceType
                                                                        .C_CODE)
                                        .map(StudyroomResourceCatalog.Candidate::id)
                                        .toList(),
                                candidates.stream()
                                                .anyMatch(
                                                        c ->
                                                                c.type()
                                                                        == StudyroomModelOutput
                                                                                .ResourceType
                                                                                .C_CODE)
                                        ? "AVAILABLE"
                                        : "UNAVAILABLE"));
            if (unit.activities().contains("PROCESS")
                    && unit.materials().stream().noneMatch(m -> "ANIMATION".equals(m.kind()))
                    && outline.exclusions().stream().noneMatch(e -> e.contains("动画"))) {
                var animations =
                        candidates.stream()
                                .filter(
                                        c ->
                                                c.type()
                                                        == StudyroomModelOutput.ResourceType
                                                                .ANIMATION)
                                .map(StudyroomResourceCatalog.Candidate::id)
                                .toList();
                if (!animations.isEmpty())
                    materials.add(
                            new ReviewedOutline.Material(
                                    "ANIMATION",
                                    "REQUIRED",
                                    "展示操作前后的状态变化",
                                    animations,
                                    "AVAILABLE"));
            }
            for (var material : unit.materials()) {
                var matches =
                        candidates.stream()
                                .filter(c -> c.type().name().equals(material.kind()))
                                .map(StudyroomResourceCatalog.Candidate::id)
                                .toList();
                if (matches.isEmpty())
                    issues.add(
                            new ReviewedOutline.Issue(
                                    "MATERIAL_UNAVAILABLE",
                                    unit.id(),
                                    "REQUIRED".equals(material.requirement())
                                            ? "BLOCKER"
                                            : "WARNING",
                                    "缺少匹配材料：" + material.kind() + "，请修改大纲或移除此要求"));
                materials.add(
                        new ReviewedOutline.Material(
                                material.kind(),
                                material.requirement(),
                                material.purpose(),
                                matches,
                                matches.isEmpty() ? "UNAVAILABLE" : "AVAILABLE"));
            }
            if (unit.activities().stream()
                            .anyMatch(a -> Set.of("IMPLEMENTATION", "EXPERIMENT").contains(a))
                    && candidates.stream()
                            .noneMatch(c -> c.type() == StudyroomModelOutput.ResourceType.C_CODE))
                issues.add(
                        new ReviewedOutline.Issue(
                                "CODE_UNAVAILABLE",
                                unit.id(),
                                "BLOCKER",
                                "实现或实验活动没有匹配代码，请调整目标后确认"));
            units.add(
                    new ReviewedOutline.Unit(
                            unit.id(),
                            unit.title(),
                            unit.objective(),
                            unit.coverageKeys(),
                            unit.expansion(),
                            unit.activities(),
                            materials));
        }
        if (question.matches(
                "(?s).*((栈|队列|二叉树)(的)?(知识|知识点)有哪些|(栈|队列|二叉树)有哪些[？?"
                    + " ]*$|完整学习|系统学习|知识总览|全部知识|所有知识|知识体系|学习路线|全面了解).*"))
            curriculum
                    .find(question)
                    .ifPresent(
                            f -> {
                                var covered =
                                        units.stream()
                                                .flatMap(u -> u.coverageKeys().stream())
                                                .collect(java.util.stream.Collectors.toSet());
                                for (var node : curriculum.recommended(f, outline.depth()))
                                    if (!covered.contains(node.key())
                                            && !outline.excludedCoverageKeys().contains(node.key()))
                                        issues.add(
                                                new ReviewedOutline.Issue(
                                                        "COVERAGE_GAP",
                                                        null,
                                                        "BLOCKER",
                                                        "路线缺少："
                                                                + node.title()
                                                                + "。可要求补充，或明确排除该知识点。"));
                            });
        return new Assessment(
                new ReviewedOutline(
                        outline.schemaVersion(),
                        outline.depth(),
                        outline.title(),
                        outline.objectives(),
                        outline.prerequisites(),
                        units,
                        outline.excludedCoverageKeys(),
                        outline.exclusions(),
                        outline.evidenceIds(),
                        null,
                        "EVIDENCE_ONLY"),
                List.copyOf(issues));
    }
}
