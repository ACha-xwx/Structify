package com.feng.dsagent.studyroom.fragment.service;

import com.feng.dsagent.studyroom.fragment.PreparationScheduler;
import com.feng.dsagent.studyroom.fragment.FragmentBudget;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentLearningMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentLearningService {
    private final FragmentLearningMapper fragments;
    private final PreparationScheduler scheduler;
    private final OutlineJson json;
    private FragmentBudget budget=new FragmentBudget(4,20000,Duration.ofMinutes(8));

    public FragmentLearningService(FragmentLearningMapper fragments,PreparationScheduler scheduler,OutlineJson json) {
        this.fragments=fragments;this.scheduler=scheduler;this.json=json;
    }

    @Autowired void configureBudget(@Value("${app.studyroom-generation.fragment-budget.maximum-calls:4}") int calls,
        @Value("${app.studyroom-generation.fragment-budget.maximum-tokens:20000}") long tokens,
        @Value("${app.studyroom-generation.fragment-budget.maximum-active-time:PT8M}") Duration time) {budget=new FragmentBudget(calls,tokens,time);}

    private Map<String,Object> currentRun(long user,String run) {
        var row=fragments.selectOwnedRun(run,user);
        if(row==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND","学习路线不存在");
        OutlineJson.require(fragments.selectRoomCurrentRun(row.get("room_id"),run)!=null,"RUN_SUPERSEDED","学习路线已更新");
        return row;
    }

    private Map<String,Object> ownedFragment(long user,String run,String id,boolean lock) {
        currentRun(user,run);
        var row=fragments.selectOwnedFragment(id,run,lock);
        if(row==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"FRAGMENT_NOT_FOUND","学习片段不存在");
        return row;
    }

    private String key(long user,String operation,String key,String payload) {
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw OutlineJson.error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","请为本次操作提供有效的 Idempotency-Key");
        var prior=fragments.findIdempotency(user,operation,key);
        if(prior==null)return null;
        OutlineJson.require(Objects.equals(prior.get("payload_hash"),OutlineJson.hash(payload)),"IDEMPOTENCY_CONFLICT","同一请求标识不能用于不同内容");
        return (String)prior.get("result_id");
    }

    private void saveKey(long user,String operation,String key,String payload,String result) {
        fragments.saveIdempotency(user,operation,key,OutlineJson.hash(payload),result);
    }

    private String enqueue(long user,String draft,String run,String unit,String type,String jobKey,Object payload) {
        var existing=fragments.selectExistingJobKey(jobKey);
        if(existing!=null)return (String)existing.get("id");
        String id=OutlineJson.id();
        fragments.insertGenerationJob(id,user,draft,run,unit,type,jobKey,json.write(payload));
        return id;
    }

    @Transactional
    public Map<String,Object> focus(long user,String run,String unit,long sequence,String key) {
        fragments.lockUser(user);currentRun(user,run);
        String payload=json.write(List.of(run,unit,sequence));
        if(key(user,"fragment_focus",key,payload)==null) {
            scheduler.requestFocus(user,run,unit,sequence);saveKey(user,"fragment_focus",key,payload,run);
        }
        return state(user,run);
    }

    @Transactional
    public Map<String,Object> progress(long user,String run,String unit,String fragment,boolean completed,String key) {
        fragments.lockUser(user);currentRun(user,run);
        String payload=json.write(List.of(run,unit,fragment,completed));
        if(key(user,"fragment_progress",key,payload)!=null)return state(user,run);
        var row=ownedFragment(user,run,fragment,true);
        OutlineJson.require(row.get("unit_id").equals(unit),"FRAGMENT_UNIT_MISMATCH","片段不属于当前知识点");
        OutlineJson.require(row.get("step_id")!=null,"FRAGMENT_NOT_READY","片段尚未就绪");
        if(completed) {
            int remaining=fragments.countRemainingFragments(unit,row.get("fragment_order"));
            OutlineJson.require(remaining==0,"KNOWLEDGE_NOT_COMPLETE","请先阅读该知识点的后续讲解");
            if(fragments.selectCompletedKnowledge(user,run,unit)==null)
                fragments.insertCompletedKnowledge(user,run,unit);
        }
        var prior=fragments.selectLearningProgressForUpdate(user,run);
        if(prior==null)fragments.insertLearningProgress(user,run,unit,fragment,completed);
        else fragments.updateLearningProgress(unit,fragment,completed,user,run);
        fragments.updateRoomLearning(row.get("step_id"),run,user);
        fragments.touchRun(run);saveKey(user,"fragment_progress",key,payload,run);
        return state(user,run);
    }

    @Transactional
    public Map<String,Object> retry(long user,String runId,String fragmentId,String key,Integer baseRevision) {
        fragments.lockUser(user);var run=currentRun(user,runId);
        String operation=baseRevision==null?"fragment_retry":"fragment_regenerate";
        String payload=json.write(Arrays.asList(runId,fragmentId,baseRevision));
        String prior=key(user,operation,key,payload);if(prior!=null)return Map.of("jobId",prior);
        var fragment=ownedFragment(user,runId,fragmentId,true);
        if(baseRevision!=null)OutlineJson.require(((Number)fragment.get("content_revision")).intValue()==baseRevision,
            "FRAGMENT_REVISION_CHANGED","内容已更新，请刷新后重试");
        var active=fragments.selectActiveFragmentJob(fragmentId);
        if(active!=null){saveKey(user,operation,key,payload,(String)active.get("id"));return Map.of("jobId",active.get("id"));}
        if(baseRevision==null&&fragment.get("status").equals("READY"))return Map.of("fragmentId",fragmentId);
        int revision;
        if(baseRevision!=null) {
            OutlineJson.require(((Number)fragment.get("content_revision")).intValue()==baseRevision,"FRAGMENT_REVISION_CHANGED","内容已更新，请刷新后重试");
            revision=fragments.nextFragmentRevision(fragmentId);
            fragments.insertFragmentVersion(fragmentId,revision);
        } else {
            revision=fragment.get("pending_revision")==null?1:((Number)fragment.get("pending_revision")).intValue();
            var version=fragments.selectFragmentVersionForUpdate(fragmentId,revision);
            OutlineJson.require(version!=null&&budget.permits(((Number)version.get("consumed_calls")).intValue(),((Number)version.get("consumed_tokens")).longValue(),Duration.ofMillis(((Number)version.get("active_duration_ms")).longValue())),
                "FRAGMENT_BUDGET_EXHAUSTED","本片段预算已用尽，请明确重新生成该片段，可能使用模型额度。");
        }
        String job=enqueue(user,(String)run.get("draft_id"),runId,(String)fragment.get("unit_id"),"FRAGMENT","fragment:"+fragmentId+":"+revision,Map.of("revision",revision));
        fragments.requeueFragmentJob(fragmentId,job);
        fragments.queueFragment(revision,fragmentId);
        fragments.touchRun(runId);saveKey(user,operation,key,payload,job);
        return Map.of("jobId",job);
    }

    private Map<String,Object> state(long user,String run) {
        return Map.of("stateVersion",fragments.selectOwnedRun(run,user).get("state_version"));
    }
}
