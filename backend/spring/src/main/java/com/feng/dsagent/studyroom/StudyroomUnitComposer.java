package com.feng.dsagent.studyroom;

import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** Generates and checkpoints one activity at a time, preserving previously validated activities. */
@Service
public class StudyroomUnitComposer {
    private final StudyroomResourceCatalog catalog;
    private final KnowledgeSearchService knowledge;
    private final GenerationCallService calls;
    private final JdbcGenerationRepository jobs;
    private final OutlineJson json;
    private final TeachingQualityGate quality;
    private final StudyroomUnitPlanner unitPlanner;

    public StudyroomUnitComposer(
            StudyroomResourceCatalog catalog,
            KnowledgeSearchService knowledge,
            GenerationCallService calls,
            JdbcGenerationRepository jobs,
            OutlineJson json,
            TeachingQualityGate quality,
            StudyroomUnitPlanner unitPlanner) {
        this.catalog = catalog;
        this.knowledge = knowledge;
        this.calls = calls;
        this.jobs = jobs;
        this.json = json;
        this.quality = quality;
        this.unitPlanner = unitPlanner;
    }

    public String compose(long user, String runId, String spec, String jobId) {
        var run =
                jobs.store()
                        .row(
                                "SELECT r.*,d.question,d.chapter_id FROM studyroom_runs r JOIN"
                                    + " study_outline_drafts d ON d.id=r.draft_id WHERE r.id=? AND"
                                    + " r.user_id=?",
                                runId,
                                user);
        OutlineJson.require(run != null, "RUN_NOT_FOUND", "学习路线不存在");
        var unit = json.read(spec, ReviewedOutline.Unit.class);
        var evidence =
                knowledge.search(
                        run.get("question") + " " + unit.objective(),
                        (String) run.get("chapter_id"),
                        6,
                        KnowledgeAudience.STUDENT);
        OutlineJson.require(!evidence.isEmpty(), "UNIT_EVIDENCE_UNAVAILABLE", "该单元暂时缺少授权资料");
        var evidenceIds = evidence.stream().map(e -> e.chunk().id()).toList();
        var allAvailable = catalog.candidates((String) run.get("chapter_id"));
        var row =
                jobs.store()
                        .row(
                                "SELECT u.* FROM studyroom_units u JOIN studyroom_generation_jobs j"
                                        + " ON j.unit_id=u.id WHERE j.id=?",
                                jobId);
        List<StudyroomModelOutput.Step> steps = new ArrayList<>();
        Set<String> usedEvidence = new LinkedHashSet<>();
        if (row.get("content_json") != null) {
            var checkpoint =
                    StudyroomModelOutput.parse(json.mapper(), (String) row.get("content_json"));
            steps.addAll(checkpoint.steps());
            usedEvidence.addAll(checkpoint.evidenceIds());
        }
        var lessonPlan = unitPlanner.plan(user, jobId, row, unit, evidence, evidenceIds);
        for (int i = 0; i < steps.size(); i++)
            if (i >= lessonPlan.steps().size()
                    || !steps.get(i).id().equals(lessonPlan.steps().get(i).id()))
                throw new IllegalArgumentException("已保存步骤与单元计划不一致");
        for (int index = steps.size(); index < lessonPlan.steps().size(); index++) {
            var goal = lessonPlan.steps().get(index);
            String activity = goal.intent();
            var selected =
                    StudyroomResourceSelector.select(
                                    allAvailable,
                                    (String) run.get("question"),
                                    goal.title() + " " + goal.objective())
                            .stream()
                            .filter(
                                    c ->
                                            c.type() != StudyroomModelOutput.ResourceType.C_CODE
                                                    || Set.of("IMPLEMENTATION", "EXPERIMENT")
                                                            .contains(activity))
                            .filter(
                                    c ->
                                            c.type() != StudyroomModelOutput.ResourceType.ANIMATION
                                                    || Set.of("PROCESS", "EXPERIMENT")
                                                            .contains(activity))
                            .toList();
            var plan =
                    new StudyroomLessonPlanner.Plan(
                            2, goal.title(), goal.objective(), evidenceIds, List.of(goal));
            Map<String, String> inputs = new HashMap<>();
            var code = catalog.codeDetails(selected);
            code.forEach(c -> inputs.put(c.get("id"), c.get("stdin")));
            String prompt =
                    json.write(
                            Map.of(
                                    "plan",
                                    plan,
                                    "unitMaterials",
                                    unit.materials().stream()
                                            .map(
                                                    m ->
                                                            Map.of(
                                                                    "kind",
                                                                    m.kind(),
                                                                    "requirement",
                                                                    m.requirement(),
                                                                    "purpose",
                                                                    m.purpose()))
                                            .toList(),
                                    "candidates",
                                    selected,
                                    "codeSources",
                                    code,
                                    "evidence",
                                    evidence));
            String bindingPolicy =
                    StudyroomSceneBinder.PROMPT
                            + "\n当前请求只生成一个步骤，steps长度必须为1，唯一id为 "
                            + goal.id()
                            + "，intent为 "
                            + goal.intent()
                            + "。只讲当前子目标，其余单元目标和材料由后续步骤处理。unitMaterials只作单元背景，本步只能引用candidates中的ID。严禁添加其他步骤。代码模式只能是"
                            + " "
                            + ("EXPERIMENT".equals(activity) ? "EXPERIMENT" : "READ")
                            + "。只引用实际支撑本步的证据ID，不要照抄整个候选证据列表。";
            String raw =
                    calls.complete(user, jobId, "activity-" + index, bindingPolicy, prompt, 5000);
            StudyroomModelOutput result;
            try {
                result = validate(raw, plan, selected, inputs, evidenceIds);
                requireActivityMaterials(result, unit, activity);
                quality.review(
                        user,
                        jobId,
                        "activity-" + index,
                        Map.of("unit", unit, "goal", goal),
                        result,
                        evidence,
                        code);
            } catch (IllegalArgumentException invalid) {
                String repaired =
                        calls.complete(
                                user,
                                jobId,
                                "repair-" + index,
                                bindingPolicy,
                                prompt
                                        + "\n仅修复当前一个步骤，保持ID与子目标，不补写整单元、不新增实验。错误："
                                        + invalid.getMessage()
                                        + "\n原输出："
                                        + raw,
                                5000);
                result = validate(repaired, plan, selected, inputs, evidenceIds);
                requireActivityMaterials(result, unit, activity);
                quality.review(
                        user,
                        jobId,
                        "repair-" + index,
                        Map.of("unit", unit, "goal", goal),
                        result,
                        evidence,
                        code);
            }
            steps.add(result.steps().getFirst());
            usedEvidence.addAll(result.evidenceIds());
            String checkpoint =
                    new StudyroomModelOutput(
                                    1,
                                    unit.title(),
                                    unit.objective(),
                                    List.copyOf(usedEvidence),
                                    steps)
                            .toJson(json.mapper());
            var lease = GenerationLease.current();
            OutlineJson.require(lease != null && jobs.active(lease), "JOB_LEASE_LOST", "生成任务租约已失效");
            jobs.store()
                    .db()
                    .update(
                            "UPDATE studyroom_units SET content_json=?,updated_at=CURRENT_TIMESTAMP"
                                    + " WHERE id=? AND status='GENERATING' AND EXISTS(SELECT 1 FROM"
                                    + " studyroom_generation_jobs WHERE id=? AND lease_token=? AND"
                                    + " status='RUNNING' AND lease_until>CURRENT_TIMESTAMP)",
                            checkpoint,
                            row.get("id"),
                            jobId,
                            lease.get("lease_token"));
        }
        var result =
                new StudyroomModelOutput(
                        1, unit.title(), unit.objective(), List.copyOf(usedEvidence), steps);
        OutlineJson.require(
                reusable(
                        result.toJson(json.mapper()),
                        unit,
                        (String) run.get("question"),
                        (String) run.get("chapter_id")),
                "UNIT_VALIDATION_FAILED",
                "单元材料或教学目标校验未通过，请调整大纲后重试");
        for (var material : unit.materials())
            if ("REQUIRED".equals(material.requirement())) {
                boolean used =
                        steps.stream()
                                .flatMap(s -> s.resources().stream())
                                .anyMatch(r -> r.type().name().equals(material.kind()));
                OutlineJson.require(
                        used,
                        "UNIT_REQUIRED_MATERIAL_MISSING",
                        "生成内容缺少已确认的必要材料：" + material.kind());
            }
        return result.toJson(json.mapper());
    }

    private StudyroomModelOutput validate(
            String raw,
            StudyroomLessonPlanner.Plan plan,
            List<StudyroomResourceCatalog.Candidate> allowed,
            Map<String, String> inputs,
            List<String> evidence) {
        String normalized;
        try {
            normalized =
                    StudyroomSceneBinder.normalize(
                            json.mapper(), StudyroomJsonEnvelope.clean(raw), plan, inputs);
        } catch (tools.jackson.core.JacksonException invalid) {
            throw new IllegalArgumentException("当前活动JSON格式错误", invalid);
        }
        var result = StudyroomModelOutput.parse(json.mapper(), normalized);
        if (!evidence.containsAll(result.evidenceIds()))
            throw new IllegalArgumentException("引用未授权证据");
        catalog.validate(result, allowed);
        StudyroomSceneBinder.validate(
                result, plan, Map.of(plan.steps().getFirst().id(), allowed), true);
        return result;
    }

    public boolean reusable(
            String raw, ReviewedOutline.Unit unit, String question, String chapter) {
        try {
            var output = StudyroomModelOutput.parse(json.mapper(), raw);
            var evidence =
                    knowledge
                            .search(
                                    question + " " + unit.objective(),
                                    chapter,
                                    6,
                                    KnowledgeAudience.STUDENT)
                            .stream()
                            .map(e -> e.chunk().id())
                            .toList();
            if (!evidence.containsAll(output.evidenceIds()) || output.steps().size() > 8)
                return false;
            var intents =
                    output.steps().stream()
                            .map(s -> s.orchestration() == null ? "" : s.orchestration().intent())
                            .collect(java.util.stream.Collectors.toSet());
            if (!intents.containsAll(unit.activities())
                    || intents.stream()
                            .anyMatch(
                                    intent ->
                                            !unit.activities().contains(intent)
                                                    && !Set.of("CONCEPT", "CHECK")
                                                            .contains(intent))) return false;
            var goals = new ArrayList<StudyroomLessonPlanner.Goal>();
            var selections = new LinkedHashMap<String, List<StudyroomResourceCatalog.Candidate>>();
            var allCandidates = catalog.candidates(chapter);
            for (var step : output.steps()) {
                if (step.orchestration() == null) return false;
                if (!"EXPERIMENT".equals(step.orchestration().intent())
                        && step.orchestration().scenes().stream()
                                .anyMatch(scene -> "EXPERIMENT".equals(scene.codeMode())))
                    return false;
                var goal =
                        new StudyroomLessonPlanner.Goal(
                                step.id(),
                                step.title(),
                                step.orchestration().objective(),
                                step.orchestration().intent());
                goals.add(goal);
                selections.put(
                        step.id(),
                        StudyroomResourceSelector.select(
                                allCandidates, question, goal.title() + " " + goal.objective()));
            }
            catalog.validate(output, allCandidates);
            StudyroomSceneBinder.validate(
                    output,
                    new StudyroomLessonPlanner.Plan(
                            2, unit.title(), unit.objective(), evidence, goals),
                    selections,
                    true);
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private void requireActivityMaterials(
            StudyroomModelOutput result, ReviewedOutline.Unit unit, String activity) {
        if (!"EXPERIMENT".equals(activity)
                && result.steps().getFirst().orchestration().scenes().stream()
                        .anyMatch(s -> "EXPERIMENT".equals(s.codeMode())))
            throw new IllegalArgumentException("当前步骤仅允许代码阅读，不得新增运行实验");
        for (var material : unit.materials())
            if ("REQUIRED".equals(material.requirement())) {
                boolean applies =
                        ("PROCESS".equals(activity) && "ANIMATION".equals(material.kind()))
                                || (Set.of("IMPLEMENTATION", "EXPERIMENT").contains(activity)
                                        && "C_CODE".equals(material.kind()));
                if (applies
                        && result.steps().getFirst().resources().stream()
                                .noneMatch(r -> r.type().name().equals(material.kind())))
                    throw new IllegalArgumentException("当前活动必须满足已确认材料：" + material.kind());
            }
    }
}
