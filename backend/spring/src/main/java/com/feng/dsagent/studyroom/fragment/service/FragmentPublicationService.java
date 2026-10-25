package com.feng.dsagent.studyroom.fragment.service;

import com.feng.dsagent.studyroom.checks.service.ClassroomCheckService;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;

import com.feng.dsagent.studyroom.fragment.FragmentPlan;

import com.feng.dsagent.studyroom.StudyroomModelOutput;
import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentPublicationMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentPublicationService {
    private final FragmentPublicationMapper fragments;
    private final OutlineJson json;
    private com.feng.dsagent.studyroom.events.service.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    public void events(com.feng.dsagent.studyroom.events.service.ClassroomEventService events) {this.events=events;}
    private com.feng.dsagent.studyroom.checks.service.ClassroomCheckService checks;
    @org.springframework.beans.factory.annotation.Autowired
    public void checks(com.feng.dsagent.studyroom.checks.service.ClassroomCheckService checks){this.checks=checks;}

    public FragmentPublicationService(FragmentPublicationMapper fragments, OutlineJson json) {
        this.fragments=fragments;this.json=json;
    }

    private Map<String,Object> currentRun(long user,String run) {
        var row=fragments.selectOwnedRun(run,user);
        if(row==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND","学习路线不存在");
        OutlineJson.require(fragments.selectRoomCurrentRun(row.get("room_id"),run)!=null,"RUN_SUPERSEDED","学习路线已更新");
        return row;
    }

    private boolean active(Map<String,Object> job) {
        return fragments.selectJobActive(job.get("id"),job.get("lease_token"))!=null;
    }

    @Transactional
    public void publish(Map<String,Object> job,String raw,String resourceRevision) {
        long user=((Number)job.get("user_id")).longValue();
        fragments.lockUser(user);
        var run=currentRun(user,(String)job.get("run_id"));
        var room=fragments.selectRoomForUpdate(run.get("room_id"));
        var fragment=fragments.selectOwnedFragment((String)job.get("fragment_id"),(String)run.get("id"));
        if(fragment==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"FRAGMENT_NOT_FOUND","学习片段不存在");
        int revision=((Number)json.object((String)job.get("payload_json")).get("revision")).intValue();
        if(((Number)fragment.get("content_revision")).intValue()==revision&&fragment.get("step_id")!=null)return;
        OutlineJson.require(active(job),"JOB_LEASE_LOST","生成任务租约已失效");
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
            var unit=fragments.selectUnitOrder(fragment.get("unit_id"));
            int order=((Number)run.get("run_revision")).intValue()*10000+((Number)unit.get("unit_order")).intValue()*100+plan.order();
            fragments.insertStep(stepId,room.get("id"),plan.key(),step.type().value(),step.title(),step.explanation(),
                json.write(output.evidenceIds()),resources,order,json.write(step.orchestration()),
                fragment.get("unit_id"),run.get("id"),run.get("id"),plan.key());
        } else fragments.updateStep(step.type().value(),step.title(),step.explanation(),json.write(output.evidenceIds()),
            resources,json.write(step.orchestration()),stepId,run.get("id"));
        fragments.updateFragmentVersionReady(raw,resourceRevision,fragment.get("id"),revision);
        fragments.updateFragmentReady(revision,stepId,raw,resourceRevision,fragment.get("id"));
        fragments.activateRun(run.get("id"));
        fragments.bumpRoom(stepId,room.get("id"));
        fragments.touchRun((String)run.get("id"));
        if(checks!=null)checks.publish(job,withChecks);
        if(events!=null)events.runUpdate(job,"content_published",Map.of("fragmentId",fragment.get("id"),"contentRevision",revision,"stepId",stepId));
        OutlineJson.require(active(job),"JOB_LEASE_LOST","生成任务已失效");
        fragments.finishJob(job.get("id"),job.get("lease_token"));
    }
}
