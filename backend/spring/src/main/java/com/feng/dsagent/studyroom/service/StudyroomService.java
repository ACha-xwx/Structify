package com.feng.dsagent.studyroom.service;

import com.feng.dsagent.studyroom.StudyroomFollowUpContext;
import com.feng.dsagent.studyroom.StudyroomResourceCatalog;
import com.feng.dsagent.studyroom.StudyroomSceneAdapter;
import com.feng.dsagent.studyroom.StudyroomRepository;
import com.feng.dsagent.studyroom.StudyroomRecords;
import com.feng.dsagent.studyroom.StudyroomScene;
import com.feng.dsagent.studyroom.StudyroomView;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudyroomService {

    private final AiQuotaExecution execution;
    private final StudyroomRepository repository;
    private final StudyroomResourceCatalog resourceCatalog;
    private final ObjectMapper mapper;

    public StudyroomService(
        AiQuotaExecution execution,
        StudyroomRepository repository,
        StudyroomResourceCatalog resourceCatalog,
        ObjectMapper mapper
    ) {
        this.execution = execution;
        this.repository = repository;
        this.resourceCatalog = resourceCatalog;
        this.mapper = mapper;
    }

    public StudyroomView get(Long userId, String studyroomId) {
        execution.requireFormalAuthentication(userId);
        StudyroomRecords.Aggregate aggregate = repository.findOwned(studyroomId, userId)
            .orElseThrow(StudyroomService::notFound);
        List<StudyroomResourceCatalog.Candidate> candidates = resourceCatalog.candidates(aggregate.room().chapterId());
        Map<String, StudyroomResourceCatalog.Candidate> indexed = candidates.stream().collect(java.util.stream.Collectors.toMap(
            candidate -> candidate.type().value() + ":" + candidate.id(), candidate -> candidate, (left, right) -> left
        ));
        List<StudyroomView.Step> steps = aggregate.steps().stream().map(step -> new StudyroomView.Step(
            step.id(), step.stepKey(), step.type(), step.title(), step.explanation(), readStrings(step.evidenceJson()),
            readResources(step.resourcesJson(), indexed), step.stepOrder(), step.supplemental(), step.parentStepId(), step.createdAt(), sceneFor(step, indexed)
        )).toList();
        List<StudyroomView.Message> messages = aggregate.messages().stream()
            .skip(Math.max(0, aggregate.messages().size() - RECENT_MESSAGE_LIMIT))
            .map(message -> new StudyroomView.Message(
                message.id(), message.role(), message.content(), readStrings(message.sourcesJson()), message.stepId(), message.createdAt()
            )).toList();
        StudyroomRecords.Room room = aggregate.room();
        return new StudyroomView(room.id(), room.chapterId(), room.title(), room.initialQuestion(), room.summary(),
            room.currentStepId(), room.version(), room.createdAt(), room.updatedAt(), steps, messages);
    }

    public StudyroomView.Progress updateProgress(Long userId, String studyroomId, String stepId) {
        execution.requireFormalAuthentication(userId);
        StudyroomRecords.Room room = repository.updateProgress(userId, studyroomId, stepId)
            .orElseThrow(StudyroomService::notFound);
        return new StudyroomView.Progress(room.currentStepId(), room.version(), room.updatedAt());
    }

    public String validateQuestionContext(long userId,String studyroomId,StudyroomFollowUpContext requested) {
        execution.requireFormalAuthentication(userId);
        var aggregate = repository.findOwned(studyroomId, userId).orElseThrow(StudyroomService::notFound);
        StudyroomFollowUpContext context=requested;
        if(context==null) {
            var current=aggregate.steps().stream().filter(s->s.id().equals(aggregate.room().currentStepId())).findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"FRAGMENT_NOT_READY","请先选择就绪的讲解"));
            var refs=resourceCatalog.candidates(aggregate.room().chapterId()).stream().collect(java.util.stream.Collectors.toMap(c->c.type().value()+":"+c.id(),c->c,(a,b)->a));
            var first=sceneFor(current,refs).scenes().getFirst();
            context=new StudyroomFollowUpContext(current.id(),first.id(),null,null,null);
        }
        final var selectedContext=context;
        var step = aggregate.steps().stream().filter(s -> s.id().equals(selectedContext.stepId())).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "STUDYROOM_CONTEXT_INVALID", "当前步骤无效"));
        var indexed = resourceCatalog.candidates(aggregate.room().chapterId()).stream().collect(java.util.stream.Collectors.toMap(
            c -> c.type().value() + ":" + c.id(), c -> c, (a,b) -> a));
        var scenes = sceneFor(step, indexed);
        var scene = scenes.scenes().stream().filter(s -> s.id().equals(selectedContext.sceneId())).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "STUDYROOM_CONTEXT_INVALID", "当前场景无效"));
        if (context.resourceId() != null && scene.items().stream().noneMatch(i -> i.resourceId().equals(selectedContext.resourceId())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "STUDYROOM_CONTEXT_INVALID", "当前资源不属于本步骤场景");
        if ((context.codeMode() != null && (!scene.kind().equals("C_CODE") || !java.util.Set.of("READ", "EXPERIMENT").contains(context.codeMode())))
            || (context.runResult() != null && (!scene.kind().equals("C_CODE") || !"EXPERIMENT".equals(context.codeMode())
                || context.resourceId() == null || (context.runResult().stdout() != null && context.runResult().stdout().length() > 4000)
                || (context.runResult().stderr() != null && context.runResult().stderr().length() > 4000))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "STUDYROOM_CONTEXT_INVALID", "代码上下文无效");
        return mapper.writeValueAsString(context);
    }

    private StudyroomScene.Orchestration sceneFor(StudyroomRecords.Step step, Map<String, StudyroomResourceCatalog.Candidate> indexed) {
        var refs = readResources(step.resourcesJson(), indexed);
        if (step.orchestrationJson() == null) throw new ApiException(HttpStatus.CONFLICT, "STUDYROOM_CONTENT_INVALID", "片段缺少展示协议，请重新准备");
        return StudyroomSceneAdapter.parse(mapper, step.orchestrationJson(), refs);
    }

    private List<String> readStrings(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "[]" : json);
            if (!root.isArray()) return List.of();
            List<String> values = new ArrayList<>();
            for (JsonNode item : root) if (item.isString()) values.add(item.asString());
            return List.copyOf(values);
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private List<StudyroomView.Resource> readResources(
        String json,
        Map<String, StudyroomResourceCatalog.Candidate> candidates
    ) {
        try {
            JsonNode root = mapper.readTree(json == null ? "[]" : json);
            if (!root.isArray()) return List.of();
            List<StudyroomView.Resource> resources = new ArrayList<>();
            for (JsonNode item : root) {
                String type = item.path("type").asString("");
                String id = item.path("id").asString("");
                if (type.isBlank() || id.isBlank()) continue;
                StudyroomResourceCatalog.Candidate candidate = candidates.get(type + ":" + id);
                Object parameters = mapper.convertValue(item.path("parameters"), Object.class);
                resources.add(new StudyroomView.Resource(
                    type, id, candidate == null ? "" : candidate.title(), candidate == null ? "" : candidate.description(),
                    parameters, candidate != null
                ));
            }
            return List.copyOf(resources);
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDYROOM_NOT_FOUND", "问答课堂不存在");
    }

    private static final int RECENT_MESSAGE_LIMIT = 100;
}
