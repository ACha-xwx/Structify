package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.studyroom.draft.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Repository
public class JdbcGenerationRepository {
    private final JdbcOutlineRepository store;
    private final OutlineJson json;
    private final TransactionTemplate tx;

    public JdbcGenerationRepository(
            JdbcOutlineRepository store, OutlineJson json, PlatformTransactionManager manager) {
        this.store = store;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    public JdbcOutlineRepository store() {
        return store;
    }

    public TransactionTemplate tx() {
        return tx;
    }

    public String enqueue(
            long user,
            String draft,
            String run,
            String unit,
            String type,
            String key,
            Object payload) {
        var existing = store.row("SELECT id FROM studyroom_generation_jobs WHERE job_key=?", key);
        if (existing != null) return (String) existing.get("id");
        String id = OutlineJson.id();
        store.db()
                .update(
                        "INSERT INTO"
                            + " studyroom_generation_jobs(id,user_id,draft_id,run_id,unit_id,job_type,job_key,status,payload_json)"
                            + " VALUES(?,?,?,?,?,?,?,'QUEUED',?)",
                        id,
                        user,
                        draft,
                        run,
                        unit,
                        type,
                        key,
                        json.write(payload));
        return id;
    }

    public Map<String, Object> claim(Instant now) {
        for (var item :
                store.db()
                        .queryForList(
                                "SELECT id,user_id FROM studyroom_generation_jobs WHERE"
                                    + " status='QUEUED' AND next_attempt_at<=CURRENT_TIMESTAMP AND"
                                    + " NOT EXISTS(SELECT 1 FROM studyroom_generation_jobs running"
                                    + " WHERE running.user_id=studyroom_generation_jobs.user_id AND"
                                    + " running.status='RUNNING') ORDER BY priority DESC,created_at,id LIMIT"
                                    + " 20")) {
            var claimed =
                    tx.execute(
                            status -> {
                                long user = ((Number) item.get("user_id")).longValue();
                                store.lockUser(user);
                                if (store.db()
                                                .queryForObject(
                                                        "SELECT COUNT(*) FROM"
                                                            + " studyroom_generation_jobs WHERE"
                                                            + " user_id=? AND status='RUNNING'",
                                                        Integer.class,
                                                        user)
                                        > 0) return null;
                                String token = OutlineJson.id();
                                int changed =
                                        store.db()
                                                .update(
                                                        "UPDATE studyroom_generation_jobs SET"
                                                            + " status='RUNNING',lease_token=?,lease_until=?,heartbeat_at=?,updated_at=?"
                                                            + " WHERE id=? AND status='QUEUED' AND"
                                                            + " next_attempt_at<=CURRENT_TIMESTAMP",
                                                        token,
                                                        Timestamp.from(now.plusSeconds(120)),
                                                        Timestamp.from(now),
                                                        Timestamp.from(now),
                                                        item.get("id"));
                                return changed == 1
                                        ? store.row(
                                                "SELECT * FROM studyroom_generation_jobs WHERE"
                                                    + " id=?",
                                                item.get("id"))
                                        : null;
                            });
            if (claimed != null) return claimed;
        }
        return null;
    }

    public boolean active(Map<String, Object> job) {
        return store.db()
                        .queryForObject(
                                "SELECT COUNT(*) FROM studyroom_generation_jobs WHERE id=? AND"
                                    + " status='RUNNING' AND lease_token=? AND"
                                    + " lease_until>CURRENT_TIMESTAMP",
                                Integer.class,
                                job.get("id"),
                                job.get("lease_token"))
                == 1;
    }

    public void heartbeat(Map<String, Object> job) {
        store.db()
                .update(
                        "UPDATE studyroom_generation_jobs SET"
                            + " lease_until=?,heartbeat_at=CURRENT_TIMESTAMP WHERE id=? AND"
                            + " lease_token=? AND status='RUNNING'",
                        Timestamp.from(Instant.now().plusSeconds(120)),
                        job.get("id"),
                        job.get("lease_token"));
    }

    public void finish(Map<String, Object> job) {
        OutlineJson.require(active(job), "JOB_LEASE_LOST", "生成任务已失效");
        store.db()
                .update(
                        "UPDATE studyroom_generation_jobs SET"
                            + " status='SUCCEEDED',updated_at=CURRENT_TIMESTAMP,lease_until=NULL"
                            + " WHERE id=? AND lease_token=? AND status='RUNNING'",
                        job.get("id"),
                        job.get("lease_token"));
    }

    public void fail(Map<String, Object> job, String code, String message) {
        tx.executeWithoutResult(
                status -> {
                    store.lockUser(((Number) job.get("user_id")).longValue());
                    if (!active(job)) return;
                    store.db()
                            .update(
                                    "UPDATE studyroom_generation_jobs SET"
                                        + " status='FAILED',failure_code=?,failure_message=?,updated_at=CURRENT_TIMESTAMP"
                                        + " WHERE id=? AND lease_token=?",
                                    code,
                                    message.substring(0, Math.min(1000, message.length())),
                                    job.get("id"),
                                    job.get("lease_token"));
                    if (job.get("fragment_id") != null) {
                        store.db().update("UPDATE studyroom_fragments SET status='FAILED',error_code=?,issues_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                            code,json.write(List.of(Map.of("code",code,"message",message))),job.get("fragment_id"));
                        store.db().update("UPDATE studyroom_runs SET state_version=state_version+1 WHERE id=?",job.get("run_id"));
                    }
                    if (job.get("unit_id") != null && job.get("fragment_id") == null)
                        store.db()
                                .update(
                                        "UPDATE studyroom_units SET"
                                            + " status='FAILED',error_code=?,error_message=? WHERE"
                                            + " id=? AND status<>'READY'",
                                        code,
                                        message.substring(0, Math.min(1000, message.length())),
                                        job.get("unit_id"));
                    if (job.get("draft_id") != null && Set.of("PLAN","REVISE").contains(job.get("job_type")))
                        restoreDraft(job);
                });
    }

    public void recover() {
        for (var job :
                store.db()
                        .queryForList(
                                "SELECT * FROM studyroom_generation_jobs WHERE status='RUNNING' AND"
                                    + " lease_until<CURRENT_TIMESTAMP")) {
            tx.executeWithoutResult(
                    status -> {
                        store.lockUser(((Number) job.get("user_id")).longValue());
                        var locked =
                                store.row(
                                        "SELECT * FROM studyroom_generation_jobs WHERE id=? FOR"
                                            + " UPDATE",
                                        job.get("id"));
                        if (!"RUNNING".equals(locked.get("status"))
                                || ((Timestamp) locked.get("lease_until"))
                                        .toInstant()
                                        .isAfter(Instant.now())) return;
                        int sent =
                                store.db()
                                        .queryForObject(
                                                "SELECT COUNT(*) FROM studyroom_generation_calls"
                                                    + " WHERE job_id=? AND status='SENT'",
                                                Integer.class,
                                                job.get("id"));
                        if (sent == 0)
                            store.db()
                                    .update(
                                            "UPDATE studyroom_generation_jobs SET"
                                                + " status='QUEUED',lease_token=NULL,lease_until=NULL"
                                                + " WHERE id=?",
                                            job.get("id"));
                        else {
                            store.db()
                                    .update(
                                            "UPDATE studyroom_generation_calls SET"
                                                + " status='UNCERTAIN',failure_code='MODEL_CALL_UNCERTAIN'"
                                                + " WHERE job_id=? AND status='SENT'",
                                            job.get("id"));
                            store.db()
                                    .update(
                                            "UPDATE studyroom_generation_jobs SET"
                                                + " status='FAILED',failure_code='MODEL_CALL_UNCERTAIN',failure_message='模型调用结果未保存，前次可能已消耗额度。可主动重试。'"
                                                + " WHERE id=?",
                                            job.get("id"));
                            if(job.get("fragment_id")!=null) {
                                store.db().update("UPDATE studyroom_fragments SET status='FAILED',error_code='MODEL_CALL_UNCERTAIN',issues_json=? WHERE id=?",
                                    json.write(List.of(Map.of("code","MODEL_CALL_UNCERTAIN","message","上次调用结果不确定，可能已消耗额度；可明确重试。"))),job.get("fragment_id"));
                                store.db().update("UPDATE studyroom_runs SET state_version=state_version+1 WHERE id=?",job.get("run_id"));
                            }
                            if (job.get("unit_id") != null && job.get("fragment_id")==null)
                                store.db()
                                        .update(
                                                "UPDATE studyroom_units SET"
                                                    + " status='FAILED',error_code='MODEL_CALL_UNCERTAIN',error_message='调用结果未保存，前次可能已消耗额度。'"
                                                    + " WHERE id=?",
                                                job.get("unit_id"));
                            if (job.get("draft_id") != null && Set.of("PLAN","REVISE").contains(job.get("job_type"))) restoreDraft(job);
                        }
                    });
        }
    }

    private void restoreDraft(Map<String, Object> job) {
        var draft =
                store.row(
                        "SELECT * FROM study_outline_drafts WHERE id=? AND pending_job_id=?",
                        job.get("draft_id"),
                        job.get("id"));
        if (draft == null) return;
        int revision = JdbcOutlineRepository.number(draft, "latest_revision");
        String depth = (String) draft.get("depth");
        if (revision > 0)
            depth =
                    json.read(
                                    (String)
                                            store.revision((String) draft.get("id"), revision)
                                                    .get("outline_json"),
                                    ReviewedOutline.class)
                            .depth()
                            .name();
        store.db()
                .update(
                        "UPDATE study_outline_drafts SET"
                            + " depth=?,status=?,pending_job_id=NULL,updated_at=CURRENT_TIMESTAMP"
                            + " WHERE id=? AND pending_job_id=?",
                        depth,
                        revision > 0 ? "REVIEWABLE" : "FAILED",
                        job.get("draft_id"),
                        job.get("id"));
    }
}
