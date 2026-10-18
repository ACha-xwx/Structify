package com.feng.dsagent.studyroom;

import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** Freezes a bounded unit plan before any individual scene is generated. */
@Service
class StudyroomUnitPlanner {
    private final JdbcGenerationRepository jobs;
    private final GenerationCallService calls;
    private final OutlineJson json;

    StudyroomUnitPlanner(
            JdbcGenerationRepository jobs, GenerationCallService calls, OutlineJson json) {
        this.jobs = jobs;
        this.calls = calls;
        this.json = json;
    }

    StudyroomLessonPlanner.Plan plan(
            long user,
            String jobId,
            Map<String, Object> row,
            ReviewedOutline.Unit unit,
            Object evidence,
            List<String> evidenceIds) {
        var cached =
                jobs.store()
                        .row(
                                "SELECT c.response_json FROM studyroom_generation_calls c JOIN"
                                    + " studyroom_generation_jobs j ON j.id=c.job_id WHERE"
                                    + " (j.unit_id=? OR j.unit_id=?) AND c.phase IN"
                                    + " ('unit-plan','unit-plan-repair') AND c.status='SUCCEEDED'"
                                    + " ORDER BY c.created_at DESC,c.id DESC LIMIT 1",
                                row.get("id"),
                                row.get("source_unit_id"));
        if (cached == null
                && row.get("source_unit_id") != null
                && row.get("content_json") != null) {
            var saved = StudyroomModelOutput.parse(json.mapper(), (String) row.get("content_json"));
            return new StudyroomLessonPlanner.Plan(
                    2,
                    unit.title(),
                    unit.objective(),
                    evidenceIds,
                    saved.steps().stream()
                            .map(
                                    step ->
                                            new StudyroomLessonPlanner.Goal(
                                                    step.id(),
                                                    step.title(),
                                                    step.orchestration().objective(),
                                                    step.orchestration().intent()))
                            .toList());
        }
        String system =
                StudyroomLessonPlanner.PROMPT
                        + "\n"
                        + "当前只展开一个已确认单元。必须覆盖输入unit的所有activities，可增加少量CONCEPT/CHECK作为引入与检查；不能新增未确认的过程、代码或运行活动，也不能加入排除范围。步骤数根据目标确定，最多8步；复杂过程可拆成多个PROCESS（如入栈和出栈分别演示），不要强制固定步数。每步标题与objective要具体且互不重复。";
        String prompt = json.write(Map.of("unit", unit, "evidence", evidence));
        String raw =
                cached == null
                        ? calls.complete(user, jobId, "unit-plan", system, prompt, 3000)
                        : (String) cached.get("response_json");
        try {
            return validatePlan(raw, unit, evidenceIds);
        } catch (IllegalArgumentException invalid) {
            return validatePlan(
                    calls.complete(
                            user,
                            jobId,
                            "unit-plan-repair",
                            system,
                            prompt
                                    + "\n修复当前单元计划，保留确认范围与必要活动。错误："
                                    + invalid.getMessage()
                                    + "\n原计划："
                                    + raw,
                            3000),
                    unit,
                    evidenceIds);
        }
    }

    private StudyroomLessonPlanner.Plan validatePlan(
            String raw, ReviewedOutline.Unit unit, List<String> evidenceIds) {
        var plan = StudyroomLessonPlanner.parse(json.mapper(), raw, new HashSet<>(evidenceIds));
        StudyroomUnitPlanPolicy.validate(plan, unit);
        return plan;
    }
}
