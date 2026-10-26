package com.feng.dsagent.admin.controller;

import com.feng.dsagent.admin.vo.AdminCapabilityVO;
import com.feng.dsagent.admin.vo.BackgroundTaskVO;
import com.feng.dsagent.admin.vo.AdminAuditEventVO;
import com.feng.dsagent.admin.vo.AdminUserVO;
import com.feng.dsagent.admin.service.BackgroundTaskService;
import com.feng.dsagent.admin.service.AdminService;
import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.common.vo.PageVO;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.feng.dsagent.admin.dto.UserStatusDTO;
import com.feng.dsagent.admin.dto.UserRolesDTO;

/**
 * 职责：管理端用户、审计与后台任务等 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService admin;
    private final BackgroundTaskService backgroundTasks;

    public AdminController(AdminService admin, BackgroundTaskService backgroundTasks) {
        this.admin = admin;
        this.backgroundTasks = backgroundTasks;
    }

    @GetMapping("/capabilities")
    public AdminCapabilityVO capabilities(@AuthenticationPrincipal AuthenticatedUser user) {
        return admin.capabilities(user);
    }

    @GetMapping("/users")
    public PageVO<AdminUserVO> users(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String role
    ) {
        return admin.users(page, size, search, status, role);
    }

    @GetMapping("/users/{id}")
    public AdminUserVO user(@PathVariable long id) {
        return admin.user(id);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserVO updateStatus(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable long id,
        @Valid @RequestBody UserStatusDTO request,
        HttpServletRequest servletRequest
    ) {
        return admin.updateStatus(actor, id, request.status(), request.reason(), requestId(servletRequest));
    }

    @PatchMapping("/users/{id}/roles")
    public AdminUserVO updateRoles(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable long id,
        @Valid @RequestBody UserRolesDTO request,
        HttpServletRequest servletRequest
    ) {
        return admin.updateRoles(actor, id, request.roles(), requestId(servletRequest));
    }

    @GetMapping("/audit-events")
    public PageVO<AdminAuditEventVO> auditEvents(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) String targetId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return admin.auditEvents(page, size, actorUserId, action, targetType, targetId, from, to);
    }

    @GetMapping("/background-tasks")
    public PageVO<BackgroundTaskVO> backgroundTasks(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String taskType
    ) {
        return backgroundTasks.list(page, size, status, taskType);
    }

    @GetMapping("/background-tasks/{id}")
    public BackgroundTaskVO backgroundTask(@PathVariable long id) {
        return backgroundTasks.task(id);
    }

    @PostMapping("/background-tasks/recover-timeouts")
    public ResponseEntity<BackgroundTaskVO> recoverTimedOutBackgroundTasks(
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        BackgroundTaskVO task = backgroundTasks.submitTimeoutRecovery(actor, requestId(servletRequest));
        backgroundTasks.dispatch(task.id());
        return ResponseEntity.accepted().body(task);
    }

    @PostMapping("/background-tasks/{id}/retry")
    public ResponseEntity<BackgroundTaskVO> retryBackgroundTask(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable long id,
        HttpServletRequest servletRequest
    ) {
        BackgroundTaskVO task = backgroundTasks.retry(actor, id, requestId(servletRequest));
        backgroundTasks.dispatch(task.id());
        return ResponseEntity.accepted().body(task);
    }

    @PostMapping("/background-tasks/{id}/cancel")
    public BackgroundTaskVO cancelBackgroundTask(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable long id,
        HttpServletRequest servletRequest
    ) {
        return backgroundTasks.cancel(actor, id, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

}
