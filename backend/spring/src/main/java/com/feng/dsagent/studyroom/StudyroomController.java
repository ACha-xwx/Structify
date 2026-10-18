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

@RestController
@RequestMapping("/api/v1/study-rooms")
public class StudyroomController {

    private final StudyroomService studyrooms;

    public StudyroomController(StudyroomService studyrooms) {
        this.studyrooms = studyrooms;
    }

    @PostMapping
    ResponseEntity<StudyroomView> create(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest request,
        @Valid @RequestBody CreateRequest body
    ) {
        throw new com.feng.dsagent.common.ApiException(org.springframework.http.HttpStatus.CONFLICT,
            "STUDYROOM_OUTLINE_REQUIRED", "请先生成并确认学习大纲，再开始学习。");
    }

    @GetMapping("/{id}")
    StudyroomView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return studyrooms.get(user.userId(), id);
    }

    @PostMapping("/{id}/messages")
    StudyroomView followUp(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest request,
        @PathVariable String id,
        @Valid @RequestBody FollowUpRequest body
    ) {
        studyrooms.followUp(user.userId(), id, body.question(), KnowledgeAudience.from(user), AiQuotaRequestId.from(request), body.context());
        return studyrooms.get(user.userId(), id);
    }

    @PatchMapping("/{id}/progress")
    StudyroomView.Progress updateProgress(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String id,
        @Valid @RequestBody ProgressRequest body
    ) {
        return studyrooms.updateProgress(user.userId(), id, body.stepId());
    }

    public record CreateRequest(@NotBlank @Size(max = 4_000) String question, @Size(max = 64) String chapterId) {
    }

    public record FollowUpRequest(@NotBlank @Size(max = 4_000) String question, @Valid StudyroomFollowUpContext context) {
    }

    public record ProgressRequest(@NotBlank @Size(max = 64) String stepId) {
    }
}
