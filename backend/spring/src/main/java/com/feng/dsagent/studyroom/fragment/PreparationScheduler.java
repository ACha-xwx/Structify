package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparationScheduler {
    private final JdbcFragmentRepository fragments;
    private final JdbcGenerationRepository jobs;
    private final OutlineJson json;
    public PreparationScheduler(JdbcFragmentRepository fragments,JdbcGenerationRepository jobs,OutlineJson json) {
        this.fragments=fragments;this.jobs=jobs;this.json=json;
    }

    @Transactional
    public void requestFocus(long user,String run,String unit,long sequence) {
        var store=fragments.store();store.lockUser(user);fragments.currentRun(user,run);
        OutlineJson.require(store.row("SELECT id FROM studyroom_units WHERE id=? AND run_id=?",unit,run)!=null,
            "UNIT_NOT_FOUND","知识点不存在");
        var prior=store.row("SELECT * FROM studyroom_learning_progress WHERE user_id=? AND run_id=? FOR UPDATE",user,run);
        if(prior!=null&&((Number)prior.get("focus_sequence")).longValue()>=sequence)return;
        if(prior==null)store.db().update("INSERT INTO studyroom_learning_progress(user_id,run_id,unit_id,focus_sequence) VALUES(?,?,?,?)",user,run,unit,sequence);
        else store.db().update("UPDATE studyroom_learning_progress SET unit_id=?,fragment_id=CASE WHEN unit_id=? THEN fragment_id ELSE NULL END,completed=FALSE,focus_sequence=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND run_id=?",unit,unit,sequence,user,run);
        fragments.touch(run);reconcile(user,run);
    }

    @Transactional
    public void reconcile(long user,String runId) {
        var store=fragments.store();store.lockUser(user);var run=fragments.currentRun(user,runId);
        var units=store.db().queryForList("SELECT id FROM studyroom_units WHERE run_id=? ORDER BY unit_order",runId);
        if(units.isEmpty())return;
        var progress=store.row("SELECT * FROM studyroom_learning_progress WHERE user_id=? AND run_id=?",user,runId);
        String focus=progress==null?(String)units.getFirst().get("id"):(String)progress.get("unit_id");
        int position=0;for(int i=0;i<units.size();i++)if(units.get(i).get("id").equals(focus))position=i;
        var desired=new LinkedHashMap<String,Integer>();
        for(var row:store.db().queryForList("SELECT id FROM studyroom_fragments WHERE unit_id=? ORDER BY fragment_order",focus))
            desired.put((String)row.get("id"),desired.isEmpty()?300:200);
        if(position+1<units.size()) {
            var next=store.row("SELECT id FROM studyroom_fragments WHERE unit_id=? ORDER BY fragment_order LIMIT 1",units.get(position+1).get("id"));
            if(next!=null)desired.put((String)next.get("id"),100);
        }
        for(var job:store.db().queryForList("SELECT id,fragment_id FROM studyroom_generation_jobs WHERE run_id=? AND job_type='FRAGMENT' AND status='QUEUED'",runId))
            if(!desired.containsKey(job.get("fragment_id"))) {
                store.db().update("UPDATE studyroom_generation_jobs SET status='CANCELLED',failure_code='FOCUS_CHANGED' WHERE id=? AND status='QUEUED'",job.get("id"));
                store.db().update("UPDATE studyroom_fragments SET status='PENDING' WHERE id=? AND status='QUEUED'",job.get("fragment_id"));
            }
        for(var entry:desired.entrySet()) {
            var fragment=store.row("SELECT * FROM studyroom_fragments WHERE id=? FOR UPDATE",entry.getKey());
            if(!Set.of("PENDING","QUEUED").contains(fragment.get("status")))continue;
            int revision=fragment.get("pending_revision")==null?1:((Number)fragment.get("pending_revision")).intValue();
            if(store.row("SELECT revision FROM studyroom_fragment_versions WHERE fragment_id=? AND revision=?",entry.getKey(),revision)==null)
                store.db().update("INSERT INTO studyroom_fragment_versions(fragment_id,revision,status) VALUES(?,?,'PENDING')",entry.getKey(),revision);
            String key="fragment:"+entry.getKey()+":"+revision;
            String job=jobs.enqueue(user,(String)run.get("draft_id"),runId,(String)fragment.get("unit_id"),"FRAGMENT",key,Map.of("revision",revision));
            store.db().update("UPDATE studyroom_generation_jobs SET fragment_id=?,priority=?,status=CASE WHEN status='CANCELLED' THEN 'QUEUED' ELSE status END,next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",entry.getKey(),entry.getValue(),job);
            store.db().update("UPDATE studyroom_fragments SET status='QUEUED',pending_revision=? WHERE id=?",revision,entry.getKey());
        }
        fragments.touch(runId);
    }
}
