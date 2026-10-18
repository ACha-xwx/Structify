package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UnitPublicationService {
    private final JdbcGenerationRepository jobs;
    private final OutlineJson json;
    private final com.feng.dsagent.compiler.CodeExecutionReadiness execution;

    public UnitPublicationService(
            JdbcGenerationRepository jobs,
            OutlineJson json,
            com.feng.dsagent.compiler.CodeExecutionReadiness execution) {
        this.jobs = jobs;
        this.json = json;
        this.execution = execution;
    }

    @Transactional
    public void publish(Map<String, Object> job, String raw) {
        var store = jobs.store();
        long user = ((Number) job.get("user_id")).longValue();
        store.lockUser(user);
        var run =
                store.row(
                        "SELECT * FROM studyroom_runs WHERE id=? AND user_id=?",
                        job.get("run_id"),
                        user);
        var room = store.row("SELECT * FROM study_rooms WHERE id=? FOR UPDATE", run.get("room_id"));
        OutlineJson.require(
                jobs.active(job) && Objects.equals(room.get("current_run_id"), run.get("id")),
                "RUN_SUPERSEDED",
                "学习路线已更新，本次生成不再发布");
        var unit =
                store.row(
                        "SELECT * FROM studyroom_units WHERE id=? AND run_id=? FOR UPDATE",
                        job.get("unit_id"),
                        run.get("id"));
        OutlineJson.require(!"READY".equals(unit.get("status")), "UNIT_ALREADY_READY", "该单元已生成");
        var content = StudyroomModelOutput.parse(json.mapper(), raw);
        int order =
                JdbcOutlineRepository.number(run, "run_revision") * 10000
                        + JdbcOutlineRepository.number(unit, "unit_order") * 100;
        String first = null;
        for (var step : content.steps()) {
            String id = OutlineJson.id();
            if (first == null) first = id;
            store.db()
                    .update(
                            "INSERT INTO"
                                + " study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,orchestration_json,unit_id,run_id,run_scope,step_key_in_unit,generation_status)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'READY')",
                            id,
                            run.get("room_id"),
                            step.id(),
                            step.type().value(),
                            step.title(),
                            step.explanation(),
                            json.write(content.evidenceIds()),
                            json.write(
                                    step.resources().stream()
                                            .map(
                                                    r ->
                                                            Map.of(
                                                                    "type",
                                                                    r.type().value(),
                                                                    "id",
                                                                    r.id(),
                                                                    "parameters",
                                                                    r.parameters()))
                                            .toList()),
                            order++,
                            json.write(step.orchestration()),
                            unit.get("id"),
                            run.get("id"),
                            run.get("id"),
                            step.id());
        }
        store.db()
                .update(
                        "UPDATE studyroom_units SET"
                            + " status='READY',content_json=?,error_code=NULL,error_message=NULL,updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=?",
                        raw,
                        unit.get("id"));
        store.db().update("UPDATE studyroom_runs SET status='ACTIVE' WHERE id=?", run.get("id"));
        store.db()
                .update(
                        "UPDATE study_rooms SET"
                            + " current_step_id=COALESCE(current_step_id,?),updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=?",
                        first,
                        run.get("room_id"));
        jobs.finish(job);
        if (JdbcOutlineRepository.number(unit, "unit_order") == 0) {
            var next =
                    store.row(
                            "SELECT * FROM studyroom_units WHERE run_id=? AND unit_order=1 AND"
                                + " status='PENDING'",
                            run.get("id"));
            if (next != null) enqueue(user, run, next);
        }
    }

    public Map<String, Object> current(long user, String roomId) {
        var room =
                jobs.store()
                        .row(
                                "SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=?",
                                roomId,
                                user);
        if (room == null)
            throw OutlineJson.error(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "STUDYROOM_NOT_FOUND",
                    "学习空间不存在");
        return room.get("current_run_id") == null
                ? Map.of("legacy", true)
                : get(user, (String) room.get("current_run_id"));
    }

    public Map<String, Object> get(long user, String runId) {
        var run =
                jobs.store()
                        .row("SELECT * FROM studyroom_runs WHERE id=? AND user_id=?", runId, user);
        if (run == null)
            throw OutlineJson.error(
                    org.springframework.http.HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "学习路线不存在");
        return Map.of(
                "codeExecutionAvailable",
                execution.available(),
                "id",
                runId,
                "roomId",
                run.get("room_id"),
                "status",
                run.get("status"),
                "outline",
                json.value((String) run.get("approved_outline_json")),
                "units",
                jobs.store()
                        .db()
                        .queryForList(
                                "SELECT id,unit_key,unit_order,status,error_code,error_message FROM"
                                    + " studyroom_units WHERE run_id=? ORDER BY unit_order",
                                runId),
                "stepUnits",
                jobs.store()
                        .db()
                        .queryForList(
                                "SELECT id,unit_id FROM study_room_steps WHERE run_id=? ORDER BY"
                                    + " step_order",
                                runId));
    }

    @Transactional
    public Map<String, Object> prepare(long user, String runId, String unitId, String key) {
        var store = jobs.store();
        store.lockUser(user);
        String payload = json.write(List.of(runId, unitId));
        String prior = store.key(user, "unit_prepare", key, payload);
        if (prior != null) return Map.of("jobId", prior);
        var run =
                store.row(
                        "SELECT r.* FROM studyroom_runs r JOIN study_rooms s ON"
                            + " s.current_run_id=r.id WHERE r.id=? AND r.user_id=?",
                        runId,
                        user);
        if (run == null)
            throw OutlineJson.error(
                    org.springframework.http.HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "当前学习路线不存在");
        var unit =
                store.row(
                        "SELECT * FROM studyroom_units WHERE id=? AND run_id=? FOR UPDATE",
                        unitId,
                        runId);
        if (unit == null)
            throw OutlineJson.error(
                    org.springframework.http.HttpStatus.NOT_FOUND, "UNIT_NOT_FOUND", "学习单元不存在");
        OutlineJson.require(!"READY".equals(unit.get("status")), "UNIT_ALREADY_READY", "该单元已经生成");
        var active =
                store.row(
                        "SELECT id FROM studyroom_generation_jobs WHERE unit_id=? AND status IN"
                            + " ('QUEUED','RUNNING') ORDER BY created_at LIMIT 1",
                        unitId);
        String job = active == null ? enqueue(user, run, unit) : (String) active.get("id");
        store.saveKey(user, "unit_prepare", key, payload, job);
        return Map.of("jobId", job);
    }

    private String enqueue(long user, Map<String, Object> run, Map<String, Object> unit) {
        String job =
                jobs.enqueue(
                        user,
                        (String) run.get("draft_id"),
                        (String) run.get("id"),
                        (String) unit.get("id"),
                        "UNIT",
                        "unit:" + unit.get("id") + ":" + OutlineJson.id(),
                        Map.of());
        jobs.store()
                .db()
                .update(
                        "UPDATE studyroom_units SET"
                            + " status='QUEUED',error_code=NULL,error_message=NULL WHERE id=?",
                        unit.get("id"));
        return job;
    }
}
