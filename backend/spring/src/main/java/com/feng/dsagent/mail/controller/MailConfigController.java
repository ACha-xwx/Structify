package com.feng.dsagent.mail.controller;

import com.feng.dsagent.mail.service.MailConfigService;

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
import com.feng.dsagent.common.vo.ConfigCapabilityVO;
import com.feng.dsagent.common.vo.ConnectionTestVO;
import com.feng.dsagent.mail.dto.MailConfigDTO;
import com.feng.dsagent.mail.dto.TestMailDTO;
import com.feng.dsagent.mail.vo.MailConfigVO;
import com.feng.dsagent.mail.vo.TestMailVO;

/**
 * 职责：管理端邮件配置的查询、保存与连通性探测 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/admin/mail-config")
@PreAuthorize("hasRole('ADMIN')")
public class MailConfigController {

    private final MailConfigService service;

    public MailConfigController(MailConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ConfigCapabilityVO<MailConfigVO> capability() {
        return service.capability();
    }

    @PutMapping
    public MailConfigVO update(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody MailConfigDTO request,
        HttpServletRequest servletRequest
    ) {
        return service.update(request, actor, requestId(servletRequest));
    }

    @PostMapping("/test-connection")
    public ConnectionTestVO testConnection(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody MailConfigDTO request,
        HttpServletRequest servletRequest
    ) {
        return service.testConnection(request, actor, requestId(servletRequest));
    }

    @PostMapping("/test-email")
    public TestMailVO testEmail(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody TestMailDTO request,
        HttpServletRequest servletRequest
    ) {
        return service.sendTestMail(request, actor, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

}
