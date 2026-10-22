package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public final class FragmentResourceResolver {
    private final StudyroomResourceCatalog catalog;
    private final KnowledgeSearchService knowledge;
    public FragmentResourceResolver(StudyroomResourceCatalog catalog,KnowledgeSearchService knowledge) {
        this.catalog=catalog;this.knowledge=knowledge;
    }
    public record ResolvedResources(Map<String,KnowledgeSearchResult> evidence,
            Map<String,StudyroomResourceCatalog.Candidate> candidates,
            Map<String,Map<String,Object>> parameters,List<Map<String,String>> code,String revision) {}

    public ResolvedResources resolve(String question,String chapter,FragmentPlan plan) {
        var matches=knowledge.searchForFragment(question+" "+plan.objective(),chapter,6,KnowledgeAudience.STUDENT);
        OutlineJson.require(!matches.isEmpty(),"FRAGMENT_EVIDENCE_UNAVAILABLE","当前讲解缺少授权依据");
        Map<String,KnowledgeSearchResult> evidence=new LinkedHashMap<>();
        matches.forEach(e->evidence.put("e"+(evidence.size()+1),e));
        var selected=StudyroomResourceSelector.select(catalog.candidates(chapter),question,plan.objective());
        Map<String,StudyroomResourceCatalog.Candidate> candidates=new LinkedHashMap<>();
        Map<String,Map<String,Object>> parameters=new LinkedHashMap<>();
        Map<StudyroomModelOutput.ResourceType,Integer> counts=new EnumMap<>(StudyroomModelOutput.ResourceType.class);
        for(var c:selected) {
            String kind=c.type().name();
            if(kind.equals("C_CODE")&&!Set.of("IMPLEMENTATION","EXPERIMENT").contains(plan.activity()))continue;
            if(kind.equals("ANIMATION")&&!Set.of("PROCESS","EXPERIMENT").contains(plan.activity()))continue;
            int count=counts.getOrDefault(c.type(),0);if(count>=(kind.equals("PPT")?4:2))continue;
            var args=kind.equals("ANIMATION")?catalog.animationDemo(c.id()):Map.<String,Object>of();
            String key="r"+(candidates.size()+1);candidates.put(key,c);parameters.put(key,args==null?Map.of():args);counts.put(c.type(),count+1);
        }
        for(String required:plan.requiredKinds())OutlineJson.require(candidates.values().stream().anyMatch(c->c.type().name().equals(required)),
            "FRAGMENT_REQUIRED_RESOURCE_MISSING","必要材料不可用："+required);
        var code=catalog.codeDetails(new ArrayList<>(candidates.values()));
        String revision=OutlineJson.hash(evidence.toString()+candidates+parameters+code);
        return new ResolvedResources(Collections.unmodifiableMap(evidence),Collections.unmodifiableMap(candidates),
            Collections.unmodifiableMap(parameters),code==null?List.of():code,revision);
    }

    public StudyroomResourceCatalog.Candidate resolveSelected(String key,ResolvedResources resolved) {
        var candidate=resolved.candidates().get(key);
        if(candidate==null)throw new IllegalArgumentException("RESOURCE_BINDING_INVALID: 未授权资源键");
        return candidate;
    }
}
