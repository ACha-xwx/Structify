package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.model.ModelClientException;
import com.feng.dsagent.studyroom.checks.service.ClassroomCheckService;
import com.feng.dsagent.studyroom.draft.service.OutlineService;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;
import com.feng.dsagent.studyroom.fragment.service.FragmentPublicationService;
import com.feng.dsagent.studyroom.generation.service.GenerationCallService;
import com.feng.dsagent.studyroom.questions.ClassroomQuestionWorker;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.fragment.*;

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
    private final FragmentComposer composer;
    private final FragmentResourceResolver resources;
    private final FragmentPublicationService publication;
    private final PreparationScheduler scheduler;
    private final OutlineJson json;
    private final boolean enabled;
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("studyroom-heartbeat").factory());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(2);
    private com.feng.dsagent.studyroom.events.service.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    public void events(com.feng.dsagent.studyroom.events.service.ClassroomEventService events) {this.events=events;}
    private com.feng.dsagent.studyroom.questions.ClassroomQuestionWorker questions;
    @org.springframework.beans.factory.annotation.Autowired
    public void questions(com.feng.dsagent.studyroom.questions.ClassroomQuestionWorker questions) {this.questions=questions;}
    private com.feng.dsagent.studyroom.checks.service.ClassroomCheckService checks;
    private GenerationCallService calls;
    @org.springframework.beans.factory.annotation.Autowired
    public void checks(com.feng.dsagent.studyroom.checks.service.ClassroomCheckService checks,GenerationCallService calls){this.checks=checks;this.calls=calls;}

    public StudyroomGenerationWorker(
            JdbcGenerationRepository jobs,
            OutlineService outlines,
            OutlineModelGateway model,
            FragmentComposer composer,
            FragmentResourceResolver resources,
            FragmentPublicationService publication,
            PreparationScheduler scheduler,
            OutlineJson json,
            @Value("${app.studyroom-generation.enabled:true}") boolean enabled) {
        this.jobs = jobs;
        this.outlines = outlines;
        this.model = model;
        this.composer = composer;
        this.resources = resources;
        this.scheduler = scheduler;
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
            if("FOLLOWUP".equals(job.get("job_type"))) {
                questions.run(job);
            } else if("CHECK".equals(job.get("job_type"))) {
                checks.run(job,calls);
            } else if ("FRAGMENT".equals(job.get("job_type"))) {
                var fragment=jobs.store().row("SELECT f.*,u.unit_spec_json,d.question,d.chapter_id FROM studyroom_fragments f JOIN studyroom_units u ON u.id=f.unit_id JOIN studyroom_runs r ON r.id=f.run_id JOIN study_outline_drafts d ON d.id=r.draft_id WHERE f.id=?",job.get("fragment_id"));
                OutlineJson.require(fragment!=null,"FRAGMENT_NOT_FOUND","学习片段不存在");
                jobs.store().db().update("UPDATE studyroom_fragments SET status='GENERATING',updated_at=CURRENT_TIMESTAMP WHERE id=?",fragment.get("id"));
                jobs.store().db().update("UPDATE studyroom_runs SET state_version=state_version+1 WHERE id=?",job.get("run_id"));
                var plan=json.read((String)fragment.get("plan_json"),FragmentPlan.class);
                if(events!=null)events.runUpdate(job,"phase",Map.of("phase","PREPARING_RESOURCES","fragmentId",fragment.get("id")));
                var resolved=resources.resolve((String)fragment.get("question"),(String)fragment.get("chapter_id"),plan);
                var context=java.util.Map.of("unit",json.value((String)fragment.get("unit_spec_json")),"question",fragment.get("question"));
                var payload=json.object((String)job.get("payload_json"));
                payload.put("executionContext",java.util.Map.of("objective",plan.objective(),"resourceRevision",resolved.revision(),"planHash",fragment.get("plan_hash")));
                jobs.store().db().update("UPDATE studyroom_generation_jobs SET payload_json=? WHERE id=?",json.write(payload),job.get("id"));
                if(events!=null)events.runUpdate(job,"phase",Map.of("phase","GENERATING_CONTENT","fragmentId",fragment.get("id")));
                String raw=composer.compose(user,(String)job.get("id"),plan,resolved,context);
                if(events!=null)events.runUpdate(job,"phase",Map.of("phase","PUBLISHING","fragmentId",fragment.get("id")));
                publication.publish(job,raw,resolved.revision());
                scheduler.reconcile(user,(String)job.get("run_id"));
            } else if(Set.of("PLAN","REVISE").contains((String)job.get("job_type"))) {
                if(events!=null)events.beginOutline(job);
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
            } else {
                throw new IllegalArgumentException("UNSUPPORTED_JOB_TYPE");
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
            if(error instanceof com.feng.dsagent.model.ModelClientException modelError)code=modelError.code();
            if(error instanceof IllegalArgumentException&&error.getMessage()!=null) {
                String prefix=error.getMessage().split("[:\\s]",2)[0];
                if(Set.of("FRAGMENT_JSON_INVALID","FRAGMENT_SCHEMA_INVALID","RESOURCE_BINDING_INVALID","EVIDENCE_INVALID",
                    "REQUIRED_RESOURCE_MISSING","CODE_LINE_OUT_OF_RANGE","CODE_SOURCE_UNAVAILABLE","CODE_EXECUTION_UNAVAILABLE",
                    "REVIEW_UNCERTAIN","TEACHING_BLOCKED").contains(prefix))code=prefix;
            }
            String message =
                    error instanceof ApiException
                            ? error.getMessage()
                            : error instanceof IllegalArgumentException
                                    ? "这段内容未通过检查，已就绪内容会保留。可重新准备当前片段，或调整大纲。"
                                    : "生成未完成，请重试。此前已完成的内容会保留。";
            if(events!=null && Set.of("PLAN","REVISE").contains((String)job.get("job_type")) && jobs.active(job)) {
                try {events.outlineUpdate(job,"failed",Map.of("code",code,"message",message),true);}
                catch(RuntimeException eventFailure) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Unable to record outline failure event for job {}",job.get("id"));}
            }
            if(events!=null&&"FRAGMENT".equals(job.get("job_type"))&&jobs.active(job)) {
                try {events.runUpdate(job,"failed",Map.of("code",code,"message",message,"fragmentId",job.get("fragment_id")));}
                catch(RuntimeException inaccessible){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Unable to record fragment failure event for job {}",job.get("id"));}
            }
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
