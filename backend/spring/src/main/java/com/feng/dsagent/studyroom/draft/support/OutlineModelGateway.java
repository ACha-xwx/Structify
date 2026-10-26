package com.feng.dsagent.studyroom.draft.support;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.compiler.support.CodeExecutionReadiness;
import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.studyroom.curriculum.support.CurriculumCatalog;
import com.feng.dsagent.studyroom.events.service.ClassroomEventService;

import com.feng.dsagent.studyroom.generation.service.GenerationCallService;

import org.springframework.stereotype.Service;

import java.util.*;
import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import com.feng.dsagent.knowledge.vo.KnowledgeSearchResultVO;
import com.feng.dsagent.studyroom.draft.prompt.OutlineModelPrompts;

import com.feng.dsagent.studyroom.draft.domain.LearningDepth;
import com.feng.dsagent.studyroom.draft.domain.ReviewedOutline;
import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;
import com.feng.dsagent.studyroom.generation.mapper.GenerationJobMapper;
@Service
public class OutlineModelGateway {
    private final GenerationCallService calls;
    private final KnowledgeSearchService knowledge;
    private final OutlineMapper outlines;
    private final GenerationJobMapper jobs;
    private final OutlineJson json;
    private final com.feng.dsagent.compiler.support.CodeExecutionReadiness execution;
    private final com.feng.dsagent.studyroom.curriculum.support.CurriculumCatalog curriculum;
    private com.feng.dsagent.studyroom.events.service.ClassroomEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    public void events(com.feng.dsagent.studyroom.events.service.ClassroomEventService events) {this.events=events;}

    public OutlineModelGateway(
            GenerationCallService calls,
            KnowledgeSearchService knowledge,
            OutlineMapper outlines,
            GenerationJobMapper jobs,
            OutlineJson json,
            com.feng.dsagent.studyroom.curriculum.support.CurriculumCatalog curriculum,
            com.feng.dsagent.compiler.support.CodeExecutionReadiness execution) {
        this.calls = calls;
        this.knowledge = knowledge;
        this.outlines = outlines;
        this.jobs = jobs;
        this.json = json;
        this.curriculum = curriculum;
        this.execution = execution;
    }

    private Map<String, Object> owned(long user, String id, boolean lock) {
        var row = outlines.selectOwnedDraft(id, user, lock);
        if (row == null)
            throw OutlineJson.error(StudyroomErrorCode.OUTLINE_NOT_FOUND);
        return row;
    }

    public String plan(
            long user,
            String question,
            LearningDepth depth,
            String previous,
            String feedback,
            String jobId) {
        var job = jobs.selectJobByUser(jobId, user);
        var draft = owned(user, (String) job.get("draft_id"), false);
        if(events!=null) {
            events.beginOutline(job);
            events.outlineUpdate(job,"phase",Map.of("phase","RETRIEVING"),false);
        }
        var evidence =
                knowledge.search(
                        question, (String) draft.get("chapter_id"), 6, KnowledgeAudience.STUDENT);
        OutlineJson.require(
                !evidence.isEmpty(), StudyroomErrorCode.OUTLINE_EVIDENCE_UNAVAILABLE);
        String system = com.feng.dsagent.studyroom.draft.prompt.OutlineModelPrompts.SYSTEM;
        if(events!=null)events.outlineUpdate(job,"phase",Map.of("phase","PLANNING"),false);
        var parser=new OutlineIncrementalParser(json,unit -> {
            if(events!=null) events.outlineUpdate(job,"outline_item",Map.of("title",unit.get("title"),"objective",unit.get("objective")),false);
        });
        String raw =
                calls.stream(
                        user,
                        jobId,
                        "outline",
                        system,
                        json.write(
                                Map.of(
                                        "question",
                                        question,
                                        "depth",
                                        depth,
                                        "previous",
                                        previous,
                                        "feedback",
                                        feedback,
                                        "evidence",
                                        evidence,
                                        "codeExecutionAvailable",
                                        execution.available(),
                                        "framework",
                                        curriculum
                                                .find(question)
                                                .map(f -> curriculum.recommended(f, depth))
                                                .orElse(List.of()))),
                        7000,parser::accept);
        if(events!=null)events.outlineUpdate(job,"phase",Map.of("phase","VALIDATING"),false);
        var parsed = json.read(raw, ReviewedOutline.class);
        var authorized = evidence.stream().map(KnowledgeSearchResultVO::id).toList();
        OutlineJson.require(
                authorized.containsAll(parsed.evidenceIds()), StudyroomErrorCode.OUTLINE_EVIDENCE_INVALID);
        return raw;
    }
}
