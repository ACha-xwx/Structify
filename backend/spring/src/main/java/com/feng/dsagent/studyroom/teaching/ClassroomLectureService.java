package com.feng.dsagent.studyroom.teaching;

import com.feng.dsagent.animation.*;
import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.fragment.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomLectureService {
    private final JdbcFragmentRepository fragments;
    private final StudyroomService rooms;
    private final StudyroomResourceCatalog catalog;
    private final DsvpLocalEngine engine;
    private final DsvpAnimationAdapter animations;
    private final OutlineJson json;
    public ClassroomLectureService(JdbcFragmentRepository fragments,StudyroomService rooms,StudyroomResourceCatalog catalog,DsvpLocalEngine engine,DsvpAnimationAdapter animations,OutlineJson json) {
        this.fragments=fragments;this.rooms=rooms;this.catalog=catalog;this.engine=engine;this.animations=animations;this.json=json;
    }
    private Map<String,Object> current(long user,String run,String fragment,int revision) {
        fragments.currentRun(user,run);var row=fragments.ownedFragment(user,run,fragment,false);
        OutlineJson.require(row.get("step_id")!=null&&((Number)row.get("content_revision")).intValue()==revision,"LECTURE_REVISION_CHANGED","讲解内容已更新，请重新打开课堂");return row;
    }
    private StudyroomView.Step step(long user,String run,Map<String,Object> fragment) {
        var owned=fragments.currentRun(user,run);
        return rooms.get(user,(String)owned.get("room_id")).steps().stream().filter(s->s.id().equals(fragment.get("step_id"))).findFirst().orElseThrow();
    }
    private List<LectureRegion> regions(String fragment,int revision) {
        return fragments.store().db().queryForList("SELECT region_json FROM studyroom_lecture_regions WHERE fragment_id=? AND content_revision=? AND deleted=FALSE ORDER BY created_at,id",fragment,revision)
            .stream().map(r->json.read((String)r.get("region_json"),LectureRegion.class)).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> get(long user,String run,String fragment,int revision) {
        var row=current(user,run,fragment,revision);var step=step(user,run,row);var regions=regions(fragment,revision);
        var room=rooms.get(user,(String)fragments.currentRun(user,run).get("room_id"));
        var lines=new HashMap<String,Integer>();
        for(var code:catalog.codeDetails(catalog.candidates(room.chapterId())))lines.put(code.get("id"),Integer.parseInt(code.get("lineCount")));
        var frames=new HashMap<String,List<String>>();
        for(var resource:step.resources())if(resource.available()&&"ANIMATION".equalsIgnoreCase(resource.type()))try {
            var intent=json.mapper().createObjectNode();intent.put("needed",true);intent.put("confidence",1.0);intent.put("capability",resource.id());intent.set("arguments",json.mapper().valueToTree(resource.parameters()==null?Map.of():resource.parameters()));
            var result=engine.resolve(intent,json.mapper().createObjectNode()).orElse(null);
            if(result!=null&&result.path("toolRequest").path("request").isObject())frames.put(resource.id(),animations.adapt(result.path("toolRequest").path("request")).animationData().steps().stream().map(s->Objects.toString(s.note(),Objects.toString(s.label(),"观察状态变化"))).toList());
        }catch(RuntimeException unavailable){ /* Text lecture remains usable without an animation. */ }
        boolean last=fragments.store().row("SELECT id FROM studyroom_fragments WHERE unit_id=? AND fragment_order>?",row.get("unit_id"),row.get("fragment_order"))==null;
        var cues=new LectureCompiler().compile(step,regions,lines,frames,last);
        return Map.of("runId",run,"fragmentId",fragment,"contentRevision",revision,"fingerprint",OutlineJson.hash(json.write(cues)),"cues",cues,"regions",regions);
    }
    @Transactional
    public LectureRegion save(long user,String run,String fragment,int revision,LectureRegion region) {
        fragments.store().lockUser(user);var row=current(user,run,fragment,revision);var step=step(user,run,row);
        boolean bound=step.orchestration().scenes().stream().anyMatch(s->s.id().equals(region.sceneId())&&s.kind().equals("PPT")&&s.items().stream().anyMatch(i->i.resourceId().equals(region.resourceId())))
            &&step.resources().stream().anyMatch(r->r.available()&&r.type().equalsIgnoreCase("PPT")&&r.id().equals(region.resourceId()));
        OutlineJson.require(bound,"LECTURE_TARGET_INVALID","区域必须属于当前讲解的可用课件");
        var store=fragments.store();var existing=store.row("SELECT * FROM studyroom_lecture_regions WHERE id=?",region.id());
        if(existing!=null) {
            OutlineJson.require(existing.get("fragment_id").equals(fragment)&&((Number)existing.get("user_id")).longValue()==user&&((Number)existing.get("content_revision")).intValue()==revision&&existing.get("region_json").equals(json.write(region))&&!Boolean.TRUE.equals(existing.get("deleted")),"LECTURE_REGION_CONFLICT","该区域标识已被使用");return region;
        }
        OutlineJson.require(regions(fragment,revision).size()<12,"LECTURE_REGION_LIMIT","每段讲解最多保存 12 个区域");
        store.db().update("INSERT INTO studyroom_lecture_regions(id,user_id,run_id,fragment_id,content_revision,scene_id,resource_id,region_json) VALUES(?,?,?,?,?,?,?,?)",region.id(),user,run,fragment,revision,region.sceneId(),region.resourceId(),json.write(region));return region;
    }
    @Transactional
    public void remove(long user,String run,String fragment,int revision,String id) {
        fragments.store().lockUser(user);current(user,run,fragment,revision);
        fragments.store().db().update("UPDATE studyroom_lecture_regions SET deleted=TRUE WHERE id=? AND user_id=? AND run_id=? AND fragment_id=? AND content_revision=?",id,user,run,fragment,revision);
    }
}
