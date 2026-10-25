package com.feng.dsagent.admin.controller;

import com.feng.dsagent.admin.service.AdminUserLearningService;

import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One learner, whole picture: courses, bookmarks, classroom work, spend, and the mastery map. Reading it is a
 * privileged act, so the service writes the audit row itself - the endpoint cannot be called without leaving a trace.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserLearningController {

    private final AdminUserLearningService learning;

    public AdminUserLearningController(AdminUserLearningService learning) {
        this.learning = learning;
    }

    @GetMapping("/{id}/learning")
    public AdminUserLearningService.AdminUserLearningView learning(
        @PathVariable long id,
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return learning.learning(id, actor.userId(), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
