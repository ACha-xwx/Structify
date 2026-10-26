package com.feng.dsagent.compiler.controller;

import com.feng.dsagent.compiler.service.SandboxConfigService;

import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.feng.dsagent.common.vo.ConnectionTestVO;
import com.feng.dsagent.compiler.dto.UpdateSandboxConfigDTO;
import com.feng.dsagent.compiler.vo.SandboxConfigCapabilityVO;
import com.feng.dsagent.compiler.vo.SandboxConfigVO;

@RestController
@RequestMapping("/api/v1/admin/sandbox-config")
@PreAuthorize("hasRole('ADMIN')")
public class SandboxConfigController {

    private final SandboxConfigService service;

    public SandboxConfigController(SandboxConfigService service) {
        this.service = service;
    }

    @GetMapping
    public SandboxConfigCapabilityVO capability() {
        return service.capability();
    }

    @PutMapping
    public SandboxConfigVO update(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody UpdateSandboxConfigDTO request,
        HttpServletRequest servletRequest
    ) {
        return service.update(request, actor, requestId(servletRequest));
    }

    @PostMapping("/test")
    public ConnectionTestVO testConnection(
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return service.testConnection(actor, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

}
