package com.feng.dsagent.learning.controller;

import com.feng.dsagent.learning.LearningWorkbenchView;
import com.feng.dsagent.learning.LearningEventCommand;
import com.feng.dsagent.learning.LearningProgressView;
import com.feng.dsagent.learning.LearningEventView;

import com.feng.dsagent.learning.service.LearningWorkbenchService;

import com.feng.dsagent.learning.service.LearningProgressService;

import com.feng.dsagent.learning.service.LearningEventService;

import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final LearningProgressService learning;
    private final LearningEventService events;
    private final LearningWorkbenchService workbench;

    public LearningController(
        LearningProgressService learning,
        LearningEventService events,
        LearningWorkbenchService workbench
    ) {
        this.learning = learning;
        this.events = events;
        this.workbench = workbench;
    }

    @GetMapping("/progress")
    public LearningProgressView progress(@AuthenticationPrincipal AuthenticatedUser user) {
        return learning.progress(user.userId());
    }

    @GetMapping("/workbench")
    public LearningWorkbenchView workbench(@AuthenticationPrincipal AuthenticatedUser user) {
        return workbench.workbench(user.userId());
    }

    @PostMapping("/events")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public LearningEventView event(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody LearningEventRequest request
    ) {
        return events.recordUserSubmitted(user.userId(), new LearningEventCommand(
            request.eventType(), request.chapterId(), request.referenceId(), request.payload()
        ));
    }

    public record LearningEventRequest(
        @NotBlank @Size(max = 48) String eventType,
        @Size(max = 64) String chapterId,
        @Size(max = 96) String referenceId,
        tools.jackson.databind.JsonNode payload
    ) {
    }
}
