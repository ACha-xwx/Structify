package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentPublicationService {
    private final JdbcFragmentRepository fragments;
    private final JdbcGenerationRepository jobs;
    private final OutlineJson json;
    private com.feng.dsagent.studyroom.events.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    void events(com.feng.dsagent.studyroom.events.ClassroomEventService events) {this.events=events;}
    private com.feng.dsagent.studyroom.checks.ClassroomCheckService checks;
    @org.springframework.beans.factory.annotation.Autowired
    void checks(com.feng.dsagent.studyroom.checks.ClassroomCheckService checks){this.checks=checks;}
    public FragmentPublicationService(JdbcFragmentRepository fragments,JdbcGenerationRepository jobs,OutlineJson json) {
        this.fragments=fragments;this.jobs=jobs;this.json=json;
    }

    @Transactional
    public void publish(Map<String,Object> job,String raw,String resourceRevision) {
        long user=((Number)job.get("user_id")).longValue();
        var store=fragments.store();store.lockUser(user);
        var run=fragments.currentRun(user,(String)job.get("run_id"));
        var room=store.row("SELECT * FROM study_rooms WHERE id=? FOR UPDATE",run.get("room_id"));
        var fragment=fragments.ownedFragment(user,(String)run.get("id"),(String)job.get("fragment_id"),true);
        int revision=((Number)json.object((String)job.get("payload_json")).get("revision")).intValue();
        if(((Number)fragment.get("content_revision")).intValue()==revision&&fragment.get("step_id")!=null)return;
        OutlineJson.require(jobs.active(job),"JOB_LEASE_LOST","生成任务租约已失效");
        OutlineJson.require(fragment.get("pending_revision")!=null&&((Number)fragment.get("pending_revision")).intValue()==revision,
            "FRAGMENT_REVISION_CHANGED","片段修订已改变");
        var plan=json.read((String)fragment.get("plan_json"),FragmentPlan.class);
        String withChecks=raw;raw=checks==null?raw:checks.core(raw);
        var output=StudyroomModelOutput.parse(json.mapper(),raw);
        OutlineJson.require(output.steps().size()==1&&output.steps().getFirst().id().equals(plan.key()),"FRAGMENT_PLAN_CHANGED","片段目标与已确认计划不一致");
        var step=output.steps().getFirst();
        String stepId=fragment.get("step_id")==null?OutlineJson.id():(String)fragment.get("step_id");
        String resources=json.write(step.resources().stream().map(r->Map.of("type",r.type().value(),"id",r.id(),"parameters",r.parameters())).toList());
        if(fragment.get("step_id")==null) {
            var unit=store.row("SELECT unit_order FROM studyroom_units WHERE id=?",fragment.get("unit_id"));
            int order=((Number)run.get("run_revision")).intValue()*10000+((Number)unit.get("unit_order")).intValue()*100+plan.order();
            store.db().update("INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,orchestration_json,unit_id,run_id,run_scope,step_key_in_unit,generation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'READY')",
                stepId,room.get("id"),plan.key(),step.type().value(),step.title(),step.explanation(),json.write(output.evidenceIds()),resources,order,
                json.write(step.orchestration()),fragment.get("unit_id"),run.get("id"),run.get("id"),plan.key());
        } else store.db().update("UPDATE study_room_steps SET step_type=?,title=?,explanation=?,evidence_json=?,resources_json=?,orchestration_json=? WHERE id=? AND run_id=?",
            step.type().value(),step.title(),step.explanation(),json.write(output.evidenceIds()),resources,json.write(step.orchestration()),stepId,run.get("id"));
        store.db().update("UPDATE studyroom_fragment_versions SET status='READY',content_json=?,resource_revision=?,issues_json=NULL WHERE fragment_id=? AND revision=?",raw,resourceRevision,fragment.get("id"),revision);
        store.db().update("UPDATE studyroom_fragments SET status='READY',content_revision=?,pending_revision=NULL,step_id=?,content_json=?,resource_revision=?,error_code=NULL,issues_json=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            revision,stepId,raw,resourceRevision,fragment.get("id"));
        store.db().update("UPDATE studyroom_runs SET status='ACTIVE' WHERE id=?",run.get("id"));
        store.db().update("UPDATE study_rooms SET current_step_id=COALESCE(current_step_id,?),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",stepId,room.get("id"));
        fragments.touch((String)run.get("id"));
        if(checks!=null)checks.publish(job,withChecks);
        if(events!=null)events.runUpdate(job,"content_published",Map.of("fragmentId",fragment.get("id"),"contentRevision",revision,"stepId",stepId));
        jobs.finish(job);
    }
}
