package com.feng.dsagent.studyroom.teaching.support;

import com.feng.dsagent.studyroom.vo.StudyroomVO;
import com.feng.dsagent.studyroom.teaching.domain.LectureRegion;
import java.util.*;

/** Compiles reviewed text and existing bindings; never invokes a model or executes code. */
public final class LectureCompiler {
    public List<Map<String,Object>> compile(StudyroomVO.Step step,List<LectureRegion> regions,Map<String,Integer> codeLines,Map<String,List<String>> animationSteps,boolean last) {
        var cues=new ArrayList<Map<String,Object>>();
        var primary=step.orchestration().primary();
        var introduction=new LinkedHashMap<String,Object>();introduction.put("kind","TEXT");introduction.put("sceneId",primary.id());
        if(!primary.items().isEmpty()&&step.resources().stream().anyMatch(r->r.available()&&r.id().equals(primary.items().getFirst().resourceId())&&r.type().equalsIgnoreCase(primary.kind()))) {
            introduction.put("kind",primary.kind());introduction.put("resourceId",primary.items().getFirst().resourceId());
            if("PPT".equals(primary.kind()))introduction.put("action","MATERIAL_FOCUS");
        }
        add(cues,step.explanation(),introduction);
        for(var scene:step.orchestration().scenes()) {
            for(var item:scene.items()) {
                var resource=step.resources().stream().filter(r->r.available()&&r.id().equals(item.resourceId())&&r.type().equalsIgnoreCase(scene.kind())).findFirst().orElse(null);
                if(resource==null)continue;
                var target=new LinkedHashMap<String,Object>();target.put("kind",scene.kind());target.put("sceneId",scene.id());target.put("resourceId",item.resourceId());
                if("PPT".equals(scene.kind()))target.put("action","MATERIAL_FOCUS");
                if("C_CODE".equals(scene.kind())&&item.startLine()!=null&&item.endLine()<=codeLines.getOrDefault(item.resourceId(),0)) {
                    target.put("action","CODE_HIGHLIGHT");target.put("startLine",item.startLine());target.put("endLine",item.endLine());
                }
                add(cues,item.explanation().isBlank()?scene.purpose():item.explanation(),target);
                if("ANIMATION".equals(scene.kind())) {
                    var labels=animationSteps.getOrDefault(item.resourceId(),List.of());int count=Math.min(32,labels.size());
                    for(int i=0;i<count;i++){var frame=new LinkedHashMap<String,Object>(target);frame.put("action","ANIMATION_SEEK");frame.put("stepIndex",i);add(cues,"第 "+(i+1)+" 步："+labels.get(i),frame);}
                }
                if("PPT".equals(scene.kind()))for(var region:regions)if(region.sceneId().equals(scene.id())&&region.resourceId().equals(item.resourceId())) {
                    var focus=new LinkedHashMap<String,Object>(target);focus.put("action","PPT_REGION");focus.put("regionId",region.id());add(cues,region.narration(),focus);
                }
            }
        }
        if(last){if(cues.size()>=160)cues.removeLast();add(cues,"停一下，完成自检，检验刚才的理解。",Map.of("kind","CHECK"));}
        return List.copyOf(cues);
    }
    private void add(List<Map<String,Object>> cues,String text,Map<String,Object> target) {
        for(String sentence:text.split("(?<=[。！？!?；;])|\\R+")) {
            sentence=sentence.strip();
            for(int start=0;start<sentence.length()&&cues.size()<160;start+=240) {
                var cue=new LinkedHashMap<String,Object>(target);cue.put("id","cue-"+cues.size());cue.put("text",sentence.substring(start,Math.min(start+240,sentence.length())));cues.add(cue);
            }
        }
    }
}
