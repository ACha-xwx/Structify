package com.feng.dsagent.studyroom;

import com.feng.dsagent.aiquota.AiQuotaRequestId;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import com.feng.dsagent.security.AuthenticatedUser;
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
import com.feng.dsagent.studyroom.questions.ClassroomQuestionService;
import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.util.Map;

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
    Map<String,Object> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        var result=json.object(json.write(studyrooms.get(user.userId(), id)));
        result.put("activeQuestionId",questions.activeQuestion(user.userId(),id));return result;
    }

    @PostMapping("/{id}/messages")
    ResponseEntity<Map<String,Object>> followUp(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest request,
        @PathVariable String id,
        @RequestHeader(value="Idempotency-Key",required=false) String key,
        @Valid @RequestBody FollowUpRequest body
    ) {
        return ResponseEntity.accepted().body(questions.create(user.userId(),id,body.runId(),body.fragmentId(),body.contentRevision(),body.question(),body.context(),key,KnowledgeAudience.from(user)));
    }

    @GetMapping("/{id}/messages/{question}")
    Map<String,Object> question(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@PathVariable String question) {return questions.get(user.userId(),id,question);}
    @PostMapping("/{id}/messages/{question}/cancel")
    Map<String,Object> cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@PathVariable String question) {return questions.cancel(user.userId(),id,question);}

    @PatchMapping("/{id}/progress")
    StudyroomView.Progress updateProgress(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody ProgressRequest body
    ) {
        return studyrooms.updateProgress(user.userId(), id, body.stepId());
    }

    public record FollowUpRequest(@NotBlank @Size(max = 4_000) String question, @Valid StudyroomFollowUpContext context,
        @NotBlank @Size(max=36) String runId,@NotBlank @Size(max=36) String fragmentId,@NotNull @Min(1) Integer contentRevision) {
    }

    public record ProgressRequest(@NotBlank @Size(max = 64) String stepId) {
    }
}
