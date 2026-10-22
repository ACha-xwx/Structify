package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.studyroom.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public final class FragmentValidator {
    private final FragmentOutputContract contract;
    private final FragmentResourceResolver resolver;
    private final StudyroomResourceCatalog catalog;
    private final CodeExecutionReadiness execution;
    public FragmentValidator(FragmentOutputContract contract,FragmentResourceResolver resolver,
            StudyroomResourceCatalog catalog,CodeExecutionReadiness execution) {
        this.contract=contract;this.resolver=resolver;this.catalog=catalog;this.execution=execution;
    }

    public StudyroomModelOutput bind(FragmentPlan plan,FragmentResourceResolver.ResolvedResources resources,String raw) {
        var body=contract.parse(raw);
        List<String> evidence=new ArrayList<>();
        for(var key:body.path("evidenceKeys")) {
            var match=resources.evidence().get(key.asString());
            if(match==null)throw new IllegalArgumentException("EVIDENCE_INVALID: 未授权依据");
            evidence.add(match.chunk().id());
        }
        List<StudyroomModelOutput.ResourceRef> refs=new ArrayList<>();
        Map<String,StudyroomScene> grouped=new LinkedHashMap<>();
        for(var scene:body.path("scenes")) {
            String key=scene.path("resourceKey").asString();
            var candidate=resolver.resolveSelected(key,resources);
            String kind=candidate.type().name();
            String mode=kind.equals("C_CODE")?(plan.activity().equals("EXPERIMENT")?"EXPERIMENT":"READ"):null;
            if("EXPERIMENT".equals(mode)&&!execution.available())throw new IllegalArgumentException("CODE_EXECUTION_UNAVAILABLE");
            if(refs.stream().anyMatch(r->r.id().equals(candidate.id())&&r.type()==candidate.type()))
                throw new IllegalArgumentException("RESOURCE_BINDING_INVALID: 重复资源");
            refs.add(new StudyroomModelOutput.ResourceRef(candidate.type(),candidate.id(),resources.parameters().getOrDefault(key,Map.of())));
            Integer start=scene.has("startLine")?scene.path("startLine").intValue():null;
            Integer end=scene.has("endLine")?scene.path("endLine").intValue():null;
            if(kind.equals("C_CODE")&&end!=null) {
                var source=resources.code().stream().filter(c->candidate.id().equals(c.get("id"))).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("CODE_SOURCE_UNAVAILABLE"));
                if(end>Integer.parseInt(source.get("lineCount")))throw new IllegalArgumentException("CODE_LINE_OUT_OF_RANGE");
            }
            if(!kind.equals("C_CODE")&&(start!=null||end!=null))throw new IllegalArgumentException("非代码场景不能指定行号");
            String groupKey=kind.equals("PPT")?kind:key;
            var items=new ArrayList<StudyroomScene.Item>();
            if(grouped.containsKey(groupKey))items.addAll(grouped.get(groupKey).items());
            items.add(new StudyroomScene.Item(candidate.id(),scene.path("explanation").asString(),start,end));
            String stdin=resources.code().stream().filter(c->candidate.id().equals(c.get("id"))).map(c->c.getOrDefault("stdin","")).findFirst().orElse("");
            grouped.put(groupKey,new StudyroomScene("scene-"+groupKey,kind,kind.equals("PPT")?"相关课件":kind.equals("C_CODE")?"代码讲解":"过程演示",
                items,mode,"EXPERIMENT".equals(mode)?plan.objective():null,stdin,
                "EXPERIMENT".equals(mode)?"根据实际运行结果检查操作和边界行为，记录观察到的差异。":null));
        }
        for(String kind:plan.requiredKinds())if(refs.stream().noneMatch(r->r.type().name().equals(kind)))
            throw new IllegalArgumentException("REQUIRED_RESOURCE_MISSING: "+kind);
        var scenes=new ArrayList<>(grouped.values());
        if(scenes.isEmpty())scenes.add(new StudyroomScene("text","TEXT","概念讲解",List.of(),null,null,null,null));
        var primary=scenes.getFirst();
        var type=switch(primary.kind()){case "PPT"->StudyroomModelOutput.StepType.PPT;case "C_CODE"->StudyroomModelOutput.StepType.C_CODE;case "ANIMATION"->StudyroomModelOutput.StepType.ANIMATION;default->StudyroomModelOutput.StepType.EXPLANATION;};
        String title=body.path("title").asString();
        var step=new StudyroomModelOutput.Step(plan.key(),type,title,body.path("explanation").asString(),refs,
            new StudyroomScene.Orchestration(2,plan.objective(),plan.activity(),primary,scenes.subList(1,scenes.size())));
        var output=new StudyroomModelOutput(1,title,title,evidence,List.of(step));
        catalog.validate(output,new ArrayList<>(resources.candidates().values()));
        var bound=refs.stream().map(r->new StudyroomView.Resource(r.type().value(),r.id(),"","",r.parameters(),true)).toList();
        StudyroomSceneAdapter.parse(new tools.jackson.databind.json.JsonMapper(),
            new tools.jackson.databind.json.JsonMapper().writeValueAsString(step.orchestration()),bound);
        return output;
    }

    public boolean requiresReview(FragmentPlan plan,StudyroomModelOutput result) {
        return plan.highRisk()||plan.activity().equals("CHECK")||Math.floorMod(plan.key().hashCode(),10)==0
            ||result.steps().getFirst().explanation().matches("(?s).*(复杂度|运行输出|运行结果|O\\(|函数|边界).*?");
    }
}
