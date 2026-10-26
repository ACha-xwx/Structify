package com.feng.dsagent.studyroom.controller;

import com.feng.dsagent.studyroom.dto.StudyroomFollowUpContextDTO;
import com.feng.dsagent.studyroom.vo.StudyroomVO;

import com.feng.dsagent.studyroom.service.StudyroomService;

import com.feng.dsagent.aiquota.support.AiQuotaRequestId;
import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import com.feng.dsagent.studyroom.questions.service.ClassroomQuestionService;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import java.util.Map;
import com.feng.dsagent.studyroom.dto.FollowUpDTO;
import com.feng.dsagent.studyroom.dto.ProgressDTO;

@RestController
@RequestMapping("/api/v1/study-rooms")
public class StudyroomController {

    private final StudyroomService studyrooms;
    private final ClassroomQuestionService questions;
    private final OutlineJson json;

    public StudyroomController(StudyroomService studyrooms,ClassroomQuestionService questions,OutlineJson json) {
        this.studyrooms = studyrooms;
        this.questions=questions;this.json=json;
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        var result=json.object(json.write(studyrooms.get(user.userId(), id)));
        result.put("activeQuestionId",questions.activeQuestion(user.userId(),id));return result;
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String,Object>> followUp(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest request,
        @PathVariable String id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,
        @Valid @RequestBody FollowUpDTO body
    ) {
        return ResponseEntity.accepted().body(questions.create(user.userId(),id,body.runId(),body.fragmentId(),body.contentRevision(),body.question(),body.context(),key,KnowledgeAudience.from(user)));
    }

    @GetMapping("/{id}/messages/{question}")
    public Map<String,Object> question(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@PathVariable String question) {return questions.get(user.userId(),id,question);}
    @PostMapping("/{id}/messages/{question}/cancel")
    public Map<String,Object> cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@PathVariable String question) {return questions.cancel(user.userId(),id,question);}

    @PatchMapping("/{id}/progress")
    public StudyroomVO.Progress updateProgress(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody ProgressDTO body
    ) {
        return studyrooms.updateProgress(user.userId(), id, body.stepId());
    }

}
