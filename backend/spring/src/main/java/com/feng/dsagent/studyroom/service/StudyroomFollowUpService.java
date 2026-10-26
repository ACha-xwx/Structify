package com.feng.dsagent.studyroom.service;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import org.springframework.stereotype.Service;
import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.studyroom.support.StudyroomResourceCatalog;
import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudyroomFollowUpService {
    private final StudyroomService rooms;
    private final KnowledgeSearchService knowledge;
    private final StudyroomResourceCatalog resources;
    private final ObjectMapper mapper;
    public StudyroomFollowUpService(StudyroomService rooms,KnowledgeSearchService knowledge,
            StudyroomResourceCatalog resources,ObjectMapper mapper) {
        this.rooms=rooms;this.knowledge=knowledge;this.resources=resources;this.mapper=mapper;
    }
    public record AnswerContext(String prompt,String stepId,String sourcesJson,String runId) {}
    public AnswerContext prepare(long user,String roomId,String question,KnowledgeAudience audience,String context) {
        var aggregate=rooms.findOwned(roomId,user).orElseThrow(()->StudyroomErrorCode.STUDYROOM_NOT_FOUND.toException("学习课堂不存在"));
        String run=rooms.currentRun(roomId,user);
        String stepId=context==null?aggregate.room().currentStepId():mapper.readTree(context).path("stepId").asString();
        var step=aggregate.steps().stream().filter(s->s.id().equals(stepId)).findFirst()
            .orElseThrow(()->StudyroomErrorCode.FRAGMENT_NOT_READY.toException());
        var matches=knowledge.searchForFragment(aggregate.room().initialQuestion()+" "+step.title()+" "+question,
            aggregate.room().chapterId(),6,audience);
        if(matches.isEmpty())throw StudyroomErrorCode.STUDYROOM_EVIDENCE_UNAVAILABLE.toException();
        Set<String> ids=new HashSet<>();for(var resource:mapper.readTree(step.resourcesJson()))ids.add(resource.path("id").asString());
        var selected=resources.candidates(aggregate.room().chapterId()).stream().filter(c->ids.contains(c.id())).toList();
        String prompt=mapper.writeValueAsString(Map.of("question",question,"publishedLesson",step.explanation(),"evidence",matches,
            "codeSources",resources.codeDetails(selected),"currentContext",context==null?"":context,
            "recentMessages",aggregate.messages().stream().skip(Math.max(0,aggregate.messages().size()-8)).toList()));
        return new AnswerContext(prompt,stepId,mapper.writeValueAsString(matches.stream().map(e->e.id()).toList()),run);
    }
}
