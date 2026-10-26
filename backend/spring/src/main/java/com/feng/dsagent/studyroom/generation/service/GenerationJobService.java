package com.feng.dsagent.studyroom.generation.service;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.studyroom.draft.domain.ReviewedOutline;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import com.feng.dsagent.studyroom.generation.mapper.GenerationJobMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GenerationJobService {
    private final GenerationJobMapper jobs;
    private final OutlineJson json;
    private final TransactionTemplate tx;

    public GenerationJobService(
            GenerationJobMapper jobs, OutlineJson json, PlatformTransactionManager manager) {
        this.jobs = jobs;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    public GenerationJobMapper mapper() {
        return jobs;
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
        var existing = jobs.selectJobByKey(key);
        if (existing != null) return (String) existing.get("id");
        String id = OutlineJson.id();
        jobs.insertJob(id, user, draft, run, unit, type, key, json.write(payload));
        return id;
    }

    public Map<String, Object> claim(Instant now) {
        for (var item : jobs.selectClaimCandidates()) {
            var claimed =
                    tx.execute(
                            status -> {
                                long user = ((Number) item.get("user_id")).longValue();
                                jobs.lockUser(user);
                                if (jobs.countRunningForUser(user) > 0) return null;
                                String token = OutlineJson.id();
                                Timestamp at = Timestamp.from(now);
                                int changed =
                                        jobs.claimQueued(
                                                token,
                                                Timestamp.from(now.plusSeconds(120)),
                                                at,
                                                at,
                                                item.get("id"));
                                return changed == 1 ? jobs.selectJob(item.get("id")) : null;
                            });
            if (claimed != null) return claimed;
        }
        return null;
    }

    public boolean active(Map<String, Object> job) {
        return jobs.countActiveLease(job.get("id"), job.get("lease_token")) == 1;
    }

    public void heartbeat(Map<String, Object> job) {
        jobs.heartbeat(
                Timestamp.from(Instant.now().plusSeconds(120)),
                job.get("id"),
                job.get("lease_token"));
    }

    public void finish(Map<String, Object> job) {
        OutlineJson.require(active(job), StudyroomErrorCode.JOB_LEASE_LOST, "生成任务已失效");
        jobs.finish(job.get("id"), job.get("lease_token"));
    }

    public void fail(Map<String, Object> job, String code, String message) {
        tx.executeWithoutResult(
                status -> {
                    jobs.lockUser(((Number) job.get("user_id")).longValue());
                    if (!active(job)) return;
                    String clipped = message.substring(0, Math.min(1000, message.length()));
                    jobs.failJob(code, clipped, job.get("id"), job.get("lease_token"));
                    if (job.get("fragment_id") != null) {
                        jobs.failFragment(
                                code,
                                json.write(List.of(Map.of("code", code, "message", message))),
                                job.get("fragment_id"));
                        jobs.touchRun(job.get("run_id"));
                    }
                    if (job.get("unit_id") != null && job.get("fragment_id") == null)
                        jobs.failUnit(code, clipped, job.get("unit_id"));
                    if (job.get("draft_id") != null
                            && Set.of("PLAN", "REVISE").contains(job.get("job_type")))
                        restoreDraft(job);
                });
    }

    public void recover() {
        for (var job : jobs.selectExpiredRunning()) {
            tx.executeWithoutResult(
                    status -> {
                        jobs.lockUser(((Number) job.get("user_id")).longValue());
                        var locked = jobs.selectJobForUpdate(job.get("id"));
                        if (!"RUNNING".equals(locked.get("status"))
                                || ((Timestamp) locked.get("lease_until"))
                                        .toInstant()
                                        .isAfter(Instant.now())) return;
                        if (jobs.countSentCalls(job.get("id")) == 0) jobs.requeueJob(job.get("id"));
                        else {
                            jobs.markCallsUncertain(job.get("id"));
                            jobs.failJobUncertain(job.get("id"));
                            if (job.get("fragment_id") != null) {
                                jobs.failFragmentUncertain(
                                        json.write(
                                                List.of(
                                                        Map.of(
                                                                "code",
                                                                "MODEL_CALL_UNCERTAIN",
                                                                "message",
                                                                "上次调用结果不确定，可能已消耗额度；可明确重试。"))),
                                        job.get("fragment_id"));
                                jobs.touchRun(job.get("run_id"));
                            }
                            if (job.get("unit_id") != null && job.get("fragment_id") == null)
                                jobs.failUnitUncertain(job.get("unit_id"));
                            if (job.get("draft_id") != null
                                    && Set.of("PLAN", "REVISE").contains(job.get("job_type")))
                                restoreDraft(job);
                        }
                    });
        }
    }

    private void restoreDraft(Map<String, Object> job) {
        var draft = jobs.selectDraftPendingJob(job.get("draft_id"), job.get("id"));
        if (draft == null) return;
        int revision = OutlineJson.number(draft, "latest_revision");
        String depth = (String) draft.get("depth");
        if (revision > 0)
            depth =
                    json.read(
                                    (String)
                                            jobs.selectRevision((String) draft.get("id"), revision)
                                                    .get("outline_json"),
                                    ReviewedOutline.class)
                            .depth()
                            .name();
        jobs.restoreDraft(
                depth,
                revision > 0 ? "REVIEWABLE" : "FAILED",
                job.get("draft_id"),
                job.get("id"));
    }
}
