package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.*;
import com.feng.dsagent.studyroom.draft.*;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Persists the model response before the job publishes it. SENT calls are never replayed
 * automatically.
 */
@Service
public class GenerationCallService {
    private final JdbcGenerationRepository jobs;
    private final AiQuotaExecution execution;

    public GenerationCallService(JdbcGenerationRepository jobs, AiQuotaExecution execution) {
        this.jobs = jobs;
        this.execution = execution;
    }

    public String complete(
            long user, String jobId, String phase, String system, String prompt, int budget) {
        var lease = GenerationLease.current();
        OutlineJson.require(lease == null || jobs.active(lease), "JOB_LEASE_LOST", "生成任务租约已失效");
        var store = jobs.store();
        String newCallId = OutlineJson.id();
        var call =
                jobs.tx()
                        .execute(
                                status -> {
                                    var job =
                                            store.row(
                                                    "SELECT * FROM studyroom_generation_jobs WHERE"
                                                        + " id=? AND user_id=? FOR UPDATE",
                                                    jobId,
                                                    user);
                                    OutlineJson.require(
                                            job != null && "RUNNING".equals(job.get("status")),
                                            "JOB_NOT_RUNNING",
                                            "生成任务已失效");
                                    if (job.get("run_id") != null)
                                        OutlineJson.require(
                                                store.db()
                                                                .queryForObject(
                                                                        "SELECT COUNT(*) FROM"
                                                                            + " studyroom_runs r"
                                                                            + " JOIN study_rooms s"
                                                                            + " ON s.current_run_id=r.id"
                                                                            + " WHERE r.id=? AND"
                                                                            + " r.user_id=?",
                                                                        Integer.class,
                                                                        job.get("run_id"),
                                                                        user)
                                                        == 1,
                                                "RUN_SUPERSEDED",
                                                "学习路线已更新，本次生成停止");
                                    OutlineJson.require(
                                            lease == null || jobs.active(lease),
                                            "JOB_LEASE_LOST",
                                            "生成任务租约已失效");
                                    var prior =
                                            store.row(
                                                    "SELECT * FROM studyroom_generation_calls WHERE"
                                                        + " job_id=? AND attempt=? AND phase=?",
                                                    jobId,
                                                    job.get("attempt"),
                                                    phase);
                                    if (prior != null) return prior;
                                    String id = newCallId;
                                    store.db()
                                            .update(
                                                    "INSERT INTO"
                                                        + " studyroom_generation_calls(id,job_id,attempt,phase,status,quota_request_id)"
                                                        + " VALUES(?,?,?,?,'SENT',?)",
                                                    id,
                                                    jobId,
                                                    job.get("attempt"),
                                                    phase,
                                                    id);
                                    return store.row(
                                            "SELECT * FROM studyroom_generation_calls WHERE id=?",
                                            id);
                                });
        if ("SUCCEEDED".equals(call.get("status"))) return (String) call.get("response_json");
        // Only the inserting caller owns a new call; a job lease prevents another worker entering
        // this phase.
        OutlineJson.require(
                newCallId.equals(call.get("id")) && "SENT".equals(call.get("status")),
                "MODEL_CALL_UNCERTAIN",
                "前次模型调用结果不确定，请主动重试");
        try {
            var response =
                    execution.complete(
                            user,
                            "study_outline",
                            (String) call.get("quota_request_id"),
                            new ModelRequest(
                                    List.of(
                                            new ModelMessage("system", system),
                                            new ModelMessage("user", prompt)),
                                    0.2,
                                    budget,
                                    true,
                                    true));
            String content = response.content();
            OutlineJson.require(
                    content != null && content.length() <= 64000,
                    "MODEL_OUTPUT_TOO_LARGE",
                    "模型输出超出单次生成上限");
            store.db()
                    .update(
                            "UPDATE studyroom_generation_calls SET"
                                + " status='SUCCEEDED',response_json=?,updated_at=CURRENT_TIMESTAMP"
                                + " WHERE id=? AND status='SENT'",
                            content,
                            call.get("id"));
            return content;
        } catch (RuntimeException error) {
            store.db()
                    .update(
                            "UPDATE studyroom_generation_calls SET"
                                + " status='FAILED',failure_code='MODEL_EXECUTION_FAILED',updated_at=CURRENT_TIMESTAMP"
                                + " WHERE id=? AND status='SENT'",
                            call.get("id"));
            throw error;
        }
    }
}
