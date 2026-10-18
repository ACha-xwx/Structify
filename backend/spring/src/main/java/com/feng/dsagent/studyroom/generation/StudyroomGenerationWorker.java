package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.studyroom.draft.*;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class StudyroomGenerationWorker {
    private final JdbcGenerationRepository jobs;
    private final OutlineService outlines;
    private final OutlineModelGateway model;
    private final UnitGenerationService units;
    private final UnitPublicationService publication;
    private final OutlineJson json;
    private final boolean enabled;
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("studyroom-heartbeat").factory());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(2);

    public StudyroomGenerationWorker(
            JdbcGenerationRepository jobs,
            OutlineService outlines,
            OutlineModelGateway model,
            UnitGenerationService units,
            UnitPublicationService publication,
            OutlineJson json,
            @Value("${app.studyroom-generation.enabled:true}") boolean enabled) {
        this.jobs = jobs;
        this.outlines = outlines;
        this.model = model;
        this.units = units;
        this.publication = publication;
        this.json = json;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        if (!enabled || !capacity.tryAcquire()) return;
        executor.submit(
                () -> {
                    try {
                        runOnce();
                    } finally {
                        capacity.release();
                    }
                });
    }

    public boolean runOnce() {
        jobs.recover();
        var job = jobs.claim(Instant.now());
        if (job == null) return false;
        var pulse =
                heartbeat.scheduleAtFixedRate(() -> jobs.heartbeat(job), 15, 15, TimeUnit.SECONDS);
        try {
            GenerationLease.enter(job);
            long user = ((Number) job.get("user_id")).longValue();
            if ("UNIT".equals(job.get("job_type"))) {
                var unit =
                        jobs.store()
                                .row(
                                        "SELECT * FROM studyroom_units WHERE id=?",
                                        job.get("unit_id"));
                jobs.store()
                        .db()
                        .update(
                                "UPDATE studyroom_units SET status='GENERATING' WHERE id=? AND"
                                    + " status='QUEUED'",
                                unit.get("id"));
                var raw =
                        units.generate(
                                user,
                                (String) job.get("run_id"),
                                (String) unit.get("unit_spec_json"),
                                (String) job.get("id"));
                publication.publish(job, raw);
            } else {
                var draft = jobs.store().owned(user, (String) job.get("draft_id"), false);
                int base = JdbcOutlineRepository.number(draft, "latest_revision");
                String previous =
                        base == 0
                                ? ""
                                : (String)
                                        jobs.store()
                                                .revision((String) draft.get("id"), base)
                                                .get("outline_json");
                var payload = json.object((String) job.get("payload_json"));
                String raw =
                        model.plan(
                                user,
                                (String) draft.get("question"),
                                LearningDepth.valueOf((String) draft.get("depth")),
                                previous,
                                (String) payload.get("feedback"),
                                (String) job.get("id"));
                outlines.complete(job, raw);
            }
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                            "Studyroom job failed: job={} error={} at={}",
                            job.get("id"),
                            error.getClass().getSimpleName(),
                            error.getStackTrace().length == 0 ? "" : error.getStackTrace()[0]);
            String code =
                    error instanceof ApiException api
                            ? api.code()
                            : error instanceof IllegalArgumentException
                                    ? "STUDYROOM_CONTENT_INVALID"
                                    : "STUDYROOM_GENERATION_FAILED";
            String message =
                    error instanceof ApiException
                            ? error.getMessage()
                            : error instanceof IllegalArgumentException
                                    ? "当前步骤未通过教学内容或材料校验，已保留完成的步骤。可重试此单元，或调整大纲后重新准备。"
                                    : "生成未完成，请重试。此前已完成的内容会保留。";
            jobs.fail(job, code, message);
        } finally {
            GenerationLease.clear();
            pulse.cancel(false);
        }
        return true;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        heartbeat.shutdownNow();
    }
}
