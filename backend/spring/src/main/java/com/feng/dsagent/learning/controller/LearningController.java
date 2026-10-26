package com.feng.dsagent.learning.controller;

import com.feng.dsagent.learning.vo.LearningWorkbenchVO;
import com.feng.dsagent.learning.dto.LearningEventCommandDTO;
import com.feng.dsagent.learning.vo.LearningProgressVO;
import com.feng.dsagent.learning.vo.LearningEventVO;

import com.feng.dsagent.learning.service.LearningWorkbenchService;

import com.feng.dsagent.learning.service.LearningProgressService;

import com.feng.dsagent.learning.service.LearningEventService;

import com.feng.dsagent.security.domain.AuthenticatedUser;
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
import com.feng.dsagent.learning.dto.LearningEventDTO;

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
    public LearningProgressVO progress(@AuthenticationPrincipal AuthenticatedUser user) {
        return learning.progress(user.userId());
    }

    @GetMapping("/workbench")
    public LearningWorkbenchVO workbench(@AuthenticationPrincipal AuthenticatedUser user) {
        return workbench.workbench(user.userId());
    }

    @PostMapping("/events")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public LearningEventVO event(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody LearningEventDTO request
    ) {
        return events.recordUserSubmitted(user.userId(), new LearningEventCommandDTO(
            request.eventType(), request.chapterId(), request.referenceId(), request.payload()
        ));
    }

}
