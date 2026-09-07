package com.feng.dsagent.compiler;

import com.feng.dsagent.common.RequestIdFilter;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sandbox-config")
@PreAuthorize("hasRole('ADMIN')")
public class SandboxConfigController {

    private final SandboxConfigService service;

    public SandboxConfigController(SandboxConfigService service) {
        this.service = service;
    }

    @GetMapping
    SandboxConfigCapabilityView capability() {
        return service.capability();
    }

    @PutMapping
    SandboxConfigView update(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody UpdateSandboxConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        return service.update(request, actor, requestId(servletRequest));
    }

    @PostMapping("/test")
    SandboxConnectionTestView testConnection(
        @AuthenticationPrincipal AuthenticatedUser actor,
        HttpServletRequest servletRequest
    ) {
        return service.testConnection(actor, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

    public record SandboxConfigCapabilityView(
        boolean available,
        String reason,
        SandboxConfigView configuration,
        SandboxRuntimeStatusView runtime
    ) {
        public SandboxConfigCapabilityView(boolean available, String reason, SandboxConfigView configuration) {
            this(available, reason, configuration, null);
        }
    }

    public record SandboxRuntimeStatusView(
        boolean codeExecutionConfigured,
        String source,
        String provider,
        Instant checkedAt
    ) {
    }

    public record SandboxConfigView(
        String provider,
        String baseUrl,
        boolean enabled,
        String lastConnectionTestStatus,
        Instant lastConnectionTestedAt,
        Instant updatedAt
    ) {
    }

    public record UpdateSandboxConfigRequest(
        @NotBlank @Size(max = 16) String provider,
        @NotBlank @Size(max = 2048) String baseUrl,
        @NotNull Boolean enabled
    ) {
    }

    public record SandboxConnectionTestView(boolean connected, String code) {
    }
}
