package com.feng.dsagent.studyroom.teaching.support;

import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TeachingActionValidator {
    private final OutlineJson json=new OutlineJson(tools.jackson.databind.json.JsonMapper.builder().build());
    public record Target(String sceneId,String resourceId,String materialKind,int lineCount,int stepCount) {}
    public record Proposed(String kind,String sceneId,String resourceId,Integer startLine,Integer endLine,Integer stepIndex) {}
    public List<Map<String,Object>> validate(List<Proposed> proposed,String run,String fragment,int revision,List<Target> targets) {
        if(proposed==null||proposed.size()>20)return List.of();
        var valid=new ArrayList<Map<String,Object>>();
        for(var action:proposed) {
            if(action==null)continue;
            var target=targets.stream().filter(t->Objects.equals(t.sceneId(),action.sceneId())&&Objects.equals(t.resourceId(),action.resourceId())).findFirst().orElse(null);
            if(target==null)continue;
            boolean supported=switch(Objects.toString(action.kind(),"")) {
                case "CODE_HIGHLIGHT" -> "C_CODE".equals(target.materialKind())&&action.startLine()!=null&&action.endLine()!=null&&action.startLine()>=1&&action.endLine()>=action.startLine()&&action.endLine()<=target.lineCount();
                case "MATERIAL_FOCUS" -> "PPT".equals(target.materialKind());
                case "ANIMATION_SEEK" -> "ANIMATION".equals(target.materialKind())&&action.stepIndex()!=null&&action.stepIndex()>=-1&&action.stepIndex()<target.stepCount();
                default -> false;
            };
            if(!supported)continue;
            var value=new LinkedHashMap<String,Object>();value.put("id",OutlineJson.id());value.put("sequence",valid.size()+1);value.put("runId",run);value.put("fragmentId",fragment);value.put("contentRevision",revision);value.put("sceneId",target.sceneId());value.put("resourceId",target.resourceId());value.put("kind",action.kind());
            if("CODE_HIGHLIGHT".equals(action.kind())){value.put("startLine",action.startLine());value.put("endLine",action.endLine());}
            if("ANIMATION_SEEK".equals(action.kind()))value.put("stepIndex",action.stepIndex());
            valid.add(value);
            if(json.write(valid).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>12000){valid.removeLast();break;}
        }
        return valid;
    }
}
