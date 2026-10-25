package com.feng.dsagent.studyroom.generation.service;

import com.feng.dsagent.studyroom.generation.GenerationLease;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.fragment.*;
import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * Persists the model response before the job publishes it. SENT calls are never replayed
 * automatically.
 */
@Service
public class GenerationCallService {
    private final GenerationCallMapper calls;
    private final TransactionTemplate tx;
    private final AiQuotaExecution execution;
    private FragmentBudget fragmentBudget = new FragmentBudget(4,20000,Duration.ofMinutes(8));
    private ObservationRegistry observations = ObservationRegistry.NOOP;
    private final OutlineJson json = new OutlineJson(tools.jackson.databind.json.JsonMapper.builder().build());

    @Autowired
    public void configureHarness(ObservationRegistry observations,
            @Value("${app.studyroom-generation.fragment-budget.maximum-calls:4}") int calls,
            @Value("${app.studyroom-generation.fragment-budget.maximum-tokens:20000}") long tokens,
            @Value("${app.studyroom-generation.fragment-budget.maximum-active-time:PT8M}") Duration time) {
        this.observations=observations;this.fragmentBudget=new FragmentBudget(calls,tokens,time);
    }

    public GenerationCallService(GenerationCallMapper calls, PlatformTransactionManager manager, AiQuotaExecution execution) {
        this.calls = calls;
        this.tx = new TransactionTemplate(manager);
        this.execution = execution;
    }

    private boolean active(Map<String,Object> job) {
        return calls.selectActiveJob(job.get("id"), job.get("lease_token")) != null;
    }

    public String complete(
            long user, String jobId, String phase, String system, String prompt, int budget) {
        return execute(user,jobId,phase,system,prompt,budget,null,true);
    }

    public String stream(long user,String jobId,String phase,String system,String prompt,int budget,java.util.function.Consumer<String> content) {
        return execute(user,jobId,phase,system,prompt,budget,Objects.requireNonNull(content),true);
    }

    public String streamText(long user,String jobId,String phase,String system,String prompt,int budget,java.util.function.Consumer<String> content) {
        return execute(user,jobId,phase,system,prompt,budget,Objects.requireNonNull(content),false);
    }

    private String execute(long user,String jobId,String phase,String system,String prompt,int budget,java.util.function.Consumer<String> delta,boolean jsonObject) {
        var lease = GenerationLease.current();
        OutlineJson.require(lease == null || active(lease), "JOB_LEASE_LOST", "生成任务租约已失效");
        String newCallId = OutlineJson.id();
        var call =
                tx.execute(
                                status -> {
                                    calls.lockUser(user);
                                    var job = calls.selectJobForUpdate(jobId, user);
                                    OutlineJson.require(
                                            job != null && "RUNNING".equals(job.get("status")),
                                            "JOB_NOT_RUNNING",
                                            "生成任务已失效");
                                    if (job.get("run_id") != null)
                                        OutlineJson.require(
                                                calls.countCurrentRun(job.get("run_id"), user) == 1,
                                                "RUN_SUPERSEDED",
                                                "学习路线已更新，本次生成停止");
                                    OutlineJson.require(
                                            lease == null || active(lease),
                                            "JOB_LEASE_LOST",
                                            "生成任务租约已失效");
                                    var prior = calls.selectPriorCall(jobId, job.get("attempt"), phase);
                                    if (prior != null) return prior;
                                    if(job.get("fragment_id")!=null)reserveFragmentBudget(job,system,prompt,budget);
                                    boolean auxiliary=Set.of("FOLLOWUP","CHECK").contains((String)job.get("job_type"));
                                    if(auxiliary)reserveAuxiliaryBudget(job,system,prompt,budget);
                                    String id = newCallId;
                                    calls.insertSentCall(id, jobId, job.get("attempt"), phase, id);
                                    if(job.get("fragment_id")!=null)calls.updateCallContext(
                                        json.write(Map.of("runId",job.get("run_id"),"fragmentId",job.get("fragment_id"),
                                            "jobId",jobId,"attempt",job.get("attempt"),"phase",phase,
                                            "promptVersion","fragment-1","schemaVersion","fragment-output-1",
                                            "estimatedTokens",estimate(system,prompt,budget))),id);
                                    if(auxiliary)calls.updateCallContext(json.write(Map.of("estimatedTokens",estimate(system,prompt,budget))),id);
                                    return calls.selectCall(id);
                                });
        if ("SUCCEEDED".equals(call.get("status"))) return (String) call.get("response_json");
        // Only the inserting caller owns a new call; a job lease prevents another worker entering
        // this phase.
        OutlineJson.require(
                newCallId.equals(call.get("id")) && "SENT".equals(call.get("status")),
                "MODEL_CALL_UNCERTAIN",
                "前次模型调用结果不确定，请主动重试");
        long started=System.nanoTime();
        var observation=Observation.start("studyroom.model.call",observations).lowCardinalityKeyValue("phase",phase);
        try {
            var request=new ModelRequest(
                                    List.of(
                                            new ModelMessage("system", system),
                                            new ModelMessage("user", prompt)),
                                    0.2,
                                    budget,
                                    jsonObject,
                                    true);
            ModelResponse response;
            if(delta==null) response=execution.complete(user,"study_outline",(String)call.get("quota_request_id"),request);
            else {
                var textBuf=new StringBuilder();
                var usage=new java.util.concurrent.atomic.AtomicReference<Long>();
                execution.stream(user,"study_outline",(String)call.get("quota_request_id"),request,new ModelStreamHandler() {
                    public void onContent(String value) {
                        if(value==null || value.isEmpty())return;
                        OutlineJson.require(System.nanoTime()-started<120_000_000_000L,"MODEL_ACTIVE_TIME_EXCEEDED","本次生成时间已超出上限");
                        OutlineJson.require(lease==null || active(lease),"JOB_LEASE_LOST","生成任务租约已失效");
                        var current=calls.selectJobStatus(jobId,user);
                        OutlineJson.require(current!=null && "RUNNING".equals(current.get("status")),"JOB_NOT_RUNNING","生成任务已停止");
                        OutlineJson.require(textBuf.length()+value.length()<=64000,"MODEL_OUTPUT_TOO_LARGE","模型输出超出单次生成上限");
                        textBuf.append(value);delta.accept(value);
                    }
                    public void onUsage(Long tokens) { if(tokens!=null && tokens>=0)usage.set(tokens); }
                });
                response=new ModelResponse(textBuf.toString(),usage.get());
            }
            String content = response.content();
            OutlineJson.require(
                    content != null && content.length() <= 64000,
                    "MODEL_OUTPUT_TOO_LARGE",
                    "模型输出超出单次生成上限");
            calls.markCallSucceeded(content, call.get("id"));
            long usage=response.totalTokens()!=null&&response.totalTokens()>=0?response.totalTokens():estimate(system,prompt,budget);
            recordUsage(call,usage,response.totalTokens()==null?"ESTIMATED":"PROVIDER_REPORTED",started,estimate(system,prompt,budget));
            observation.lowCardinalityKeyValue("outcome","success");
            return content;
        } catch (RuntimeException error) {
            long usage=error instanceof ModelClientException modelError&&modelError.consumedTokens()!=null
                ? modelError.consumedTokens():estimate(system,prompt,budget);
            recordUsage(call,usage,"FAILED_OR_ESTIMATED",started,estimate(system,prompt,budget));
            observation.lowCardinalityKeyValue("outcome","failed").error(error);
            calls.markCallFailed(call.get("id"));
            throw error;
        } finally {
            observation.stop();
        }
    }

    private long estimate(String system,String prompt,int output) {
        return (system.length()+prompt.length()+3L)/4L+output;
    }

    private void reserveAuxiliaryBudget(Map<String,Object> job,String system,String prompt,int output) {
        var previous=calls.selectCallsByJob(job.get("id"));
        long tokens=0,time=0;
        for(var call:previous) {
            var usage=call.get("usage_json")==null?null:json.object((String)call.get("usage_json"));
            var context=call.get("request_context_json")==null?Map.<String,Object>of():json.object((String)call.get("request_context_json"));
            tokens+=((Number)(usage==null?context.getOrDefault("estimatedTokens",20000):usage.get("totalTokens"))).longValue();
            if(call.get("elapsed_ms")!=null)time+=((Number)call.get("elapsed_ms")).longValue();
        }
        boolean check="CHECK".equals(job.get("job_type"));
        OutlineJson.require(previous.size()<(check?1:2)&&tokens+estimate(system,prompt,output)<=(check?10000:20000)&&time<120000,"CLASSROOM_BUDGET_EXHAUSTED","本次课堂任务预算已用尽，已完成的回答会保留");
    }

    public void recordReview(long user,String job,String phase,String raw) {
        String issues;
        try{issues=json.write(json.mapper().readTree(raw).path("issues"));}
        catch(RuntimeException error){issues=json.write(List.of(Map.of("code","REVIEW_UNCERTAIN","message","审查结果格式不确定")));}
        calls.updateCallIssues(issues,job,phase,user);
    }

    private void reserveFragmentBudget(Map<String,Object> job,String system,String prompt,int output) {
        String fragment=(String)job.get("fragment_id");
        int revision=((Number)json.object((String)job.get("payload_json")).get("revision")).intValue();
        var version=calls.selectFragmentVersionForUpdate(fragment,revision);
        OutlineJson.require(version!=null,"FRAGMENT_REVISION_MISSING","片段修订不存在");
        long charged=((Number)version.get("consumed_tokens")).longValue();
        boolean permitted=fragmentBudget.permits(((Number)version.get("consumed_calls")).intValue(),charged,
            Duration.ofMillis(((Number)version.get("active_duration_ms")).longValue()));
        long reserved=estimate(system,prompt,output);
        OutlineJson.require(permitted&&reserved<=fragmentBudget.maximumTokens()-charged,"FRAGMENT_BUDGET_EXHAUSTED",
            "本片段生成预算已用尽；可明确重新生成该片段，已发布内容会保留。");
        calls.reserveFragmentBudget(reserved,fragment,revision);
    }

    private void recordUsage(Map<String,Object> call,long tokens,String source,long started,long reserved) {
        long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        tx.executeWithoutResult(status->{
            var job=calls.selectJob(call.get("job_id"));
            calls.updateCallUsage(json.write(Map.of("totalTokens",tokens,"source",source)),elapsed,call.get("id"));
            if(job!=null&&job.get("fragment_id")!=null) {
                int revision=((Number)json.object((String)job.get("payload_json")).get("revision")).intValue();
                calls.adjustFragmentUsage(tokens-reserved,elapsed,job.get("fragment_id"),revision);
            }
        });
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Studyroom model call job={} call={} phase={} elapsedMs={} tokens={} usageSource={}",
            call.get("job_id"),call.get("id"),call.get("phase"),elapsed,tokens,source);
    }
}
