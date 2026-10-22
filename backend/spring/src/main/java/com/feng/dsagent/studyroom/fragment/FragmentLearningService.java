package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentLearningService {
    private final JdbcFragmentRepository fragments;
    private final JdbcGenerationRepository jobs;
    private final PreparationScheduler scheduler;
    private final OutlineJson json;
    private FragmentBudget budget=new FragmentBudget(4,20000,Duration.ofMinutes(8));
    public FragmentLearningService(JdbcFragmentRepository fragments,JdbcGenerationRepository jobs,PreparationScheduler scheduler,OutlineJson json) {
        this.fragments=fragments;this.jobs=jobs;this.scheduler=scheduler;this.json=json;
    }
    @Autowired void configureBudget(@Value("${app.studyroom-generation.fragment-budget.maximum-calls:4}") int calls,
        @Value("${app.studyroom-generation.fragment-budget.maximum-tokens:20000}") long tokens,
        @Value("${app.studyroom-generation.fragment-budget.maximum-active-time:PT8M}") Duration time) {budget=new FragmentBudget(calls,tokens,time);}

    @Transactional
    public Map<String,Object> focus(long user,String run,String unit,long sequence,String key) {
        var store=fragments.store();store.lockUser(user);fragments.currentRun(user,run);
        String payload=json.write(List.of(run,unit,sequence));
        if(store.key(user,"fragment_focus",key,payload)==null) {
            scheduler.requestFocus(user,run,unit,sequence);store.saveKey(user,"fragment_focus",key,payload,run);
        }
        return state(user,run);
    }

    @Transactional
    public Map<String,Object> progress(long user,String run,String unit,String fragment,boolean completed,String key) {
        var store=fragments.store();store.lockUser(user);fragments.currentRun(user,run);
        String payload=json.write(List.of(run,unit,fragment,completed));
        if(store.key(user,"fragment_progress",key,payload)!=null)return state(user,run);
        var row=fragments.ownedFragment(user,run,fragment,true);
        OutlineJson.require(row.get("unit_id").equals(unit),"FRAGMENT_UNIT_MISMATCH","片段不属于当前知识点");
        OutlineJson.require(row.get("step_id")!=null,"FRAGMENT_NOT_READY","片段尚未就绪");
        if(completed) {
            int remaining=store.db().queryForObject("SELECT COUNT(*) FROM studyroom_fragments WHERE unit_id=? AND (step_id IS NULL OR fragment_order>?)",Integer.class,unit,row.get("fragment_order"));
            OutlineJson.require(remaining==0,"KNOWLEDGE_NOT_COMPLETE","请先阅读该知识点的后续讲解");
            if(store.row("SELECT unit_id FROM studyroom_completed_knowledge WHERE user_id=? AND run_id=? AND unit_id=?",user,run,unit)==null)
                store.db().update("INSERT INTO studyroom_completed_knowledge(user_id,run_id,unit_id) VALUES(?,?,?)",user,run,unit);
        }
        var prior=store.row("SELECT * FROM studyroom_learning_progress WHERE user_id=? AND run_id=? FOR UPDATE",user,run);
        if(prior==null)store.db().update("INSERT INTO studyroom_learning_progress(user_id,run_id,unit_id,fragment_id,completed) VALUES(?,?,?,?,?)",user,run,unit,fragment,completed);
        else store.db().update("UPDATE studyroom_learning_progress SET unit_id=?,fragment_id=?,completed=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND run_id=?",unit,fragment,completed,user,run);
        store.db().update("UPDATE study_rooms SET current_step_id=?,last_learning_at=CURRENT_TIMESTAMP WHERE current_run_id=? AND user_id=?",row.get("step_id"),run,user);
        fragments.touch(run);store.saveKey(user,"fragment_progress",key,payload,run);
        return state(user,run);
    }

    @Transactional
    public Map<String,Object> retry(long user,String runId,String fragmentId,String key,Integer baseRevision) {
        var store=fragments.store();store.lockUser(user);var run=fragments.currentRun(user,runId);
        String operation=baseRevision==null?"fragment_retry":"fragment_regenerate";
        String payload=json.write(Arrays.asList(runId,fragmentId,baseRevision));
        String prior=store.key(user,operation,key,payload);if(prior!=null)return Map.of("jobId",prior);
        var fragment=fragments.ownedFragment(user,runId,fragmentId,true);
        if(baseRevision!=null)OutlineJson.require(((Number)fragment.get("content_revision")).intValue()==baseRevision,
            "FRAGMENT_REVISION_CHANGED","内容已更新，请刷新后重试");
        var active=store.row("SELECT id FROM studyroom_generation_jobs WHERE fragment_id=? AND status IN ('QUEUED','RUNNING')",fragmentId);
        if(active!=null){store.saveKey(user,operation,key,payload,(String)active.get("id"));return Map.of("jobId",active.get("id"));}
        if(baseRevision==null&&fragment.get("status").equals("READY"))return Map.of("fragmentId",fragmentId);
        int revision;
        if(baseRevision!=null) {
            OutlineJson.require(((Number)fragment.get("content_revision")).intValue()==baseRevision,"FRAGMENT_REVISION_CHANGED","内容已更新，请刷新后重试");
            revision=store.db().queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM studyroom_fragment_versions WHERE fragment_id=?",Integer.class,fragmentId);
            store.db().update("INSERT INTO studyroom_fragment_versions(fragment_id,revision,status) VALUES(?,?,'PENDING')",fragmentId,revision);
        } else {
            revision=fragment.get("pending_revision")==null?1:((Number)fragment.get("pending_revision")).intValue();
            var version=store.row("SELECT * FROM studyroom_fragment_versions WHERE fragment_id=? AND revision=? FOR UPDATE",fragmentId,revision);
            OutlineJson.require(version!=null&&budget.permits(((Number)version.get("consumed_calls")).intValue(),((Number)version.get("consumed_tokens")).longValue(),Duration.ofMillis(((Number)version.get("active_duration_ms")).longValue())),
                "FRAGMENT_BUDGET_EXHAUSTED","本片段预算已用尽，请明确重新生成该片段，可能使用模型额度。");
        }
        String job=jobs.enqueue(user,(String)run.get("draft_id"),runId,(String)fragment.get("unit_id"),"FRAGMENT","fragment:"+fragmentId+":"+revision,Map.of("revision",revision));
        store.db().update("UPDATE studyroom_generation_jobs SET fragment_id=?,priority=300,attempt=CASE WHEN status IN ('FAILED','CANCELLED') THEN attempt+1 ELSE attempt END,status='QUEUED',lease_token=NULL,lease_until=NULL,failure_code=NULL,failure_message=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",fragmentId,job);
        store.db().update("UPDATE studyroom_fragments SET status='QUEUED',pending_revision=?,error_code=NULL,issues_json=NULL WHERE id=?",revision,fragmentId);
        fragments.touch(runId);store.saveKey(user,operation,key,payload,job);
        return Map.of("jobId",job);
    }
    private Map<String,Object> state(long user,String run) {return Map.of("stateVersion",fragments.ownedRun(user,run).get("state_version"));}
}
