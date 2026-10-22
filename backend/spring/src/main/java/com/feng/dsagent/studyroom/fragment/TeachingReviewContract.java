package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class TeachingReviewContract {
    private final OutlineJson json;
    public TeachingReviewContract(OutlineJson json){this.json=json;}
    public void validate(String raw,Set<String> allowedEvidence) {
        try {
            var body=json.mapper().readTree(raw);
            if(!body.path("valid").isBoolean()||!body.path("issues").isArray()||!body.path("reason").isString()
                ||body.path("reason").asString().isBlank()||body.path("issues").size()>20)
                throw new IllegalArgumentException("REVIEW_UNCERTAIN");
            List<String> blocking=new ArrayList<>();
            for(var issue:body.path("issues")) {
                if(!Set.of("BLOCKER","ADVICE").contains(issue.path("severity").asString(""))
                    ||!issue.path("message").isString()||issue.path("message").asString().isBlank()
                    ||!issue.path("code").isString()||issue.path("code").asString().isBlank()
                    ||!issue.path("evidenceKeys").isArray())throw new IllegalArgumentException("REVIEW_UNCERTAIN");
                for(var key:issue.path("evidenceKeys"))if(!key.isString()||!allowedEvidence.contains(key.asString()))throw new IllegalArgumentException("REVIEW_UNCERTAIN");
                if(issue.path("severity").asString().equals("BLOCKER"))blocking.add(issue.path("message").asString());
            }
            if(!body.path("valid").asBoolean()||!blocking.isEmpty())throw new IllegalArgumentException("TEACHING_BLOCKED: "+blocking+" "+body.path("reason").asString());
        } catch(IllegalArgumentException error){throw error;}
        catch(RuntimeException error){throw new IllegalArgumentException("REVIEW_UNCERTAIN",error);}
    }
}
