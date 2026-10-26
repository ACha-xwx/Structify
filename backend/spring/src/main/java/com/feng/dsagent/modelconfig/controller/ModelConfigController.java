package com.feng.dsagent.modelconfig.controller;


import com.feng.dsagent.modelconfig.service.ModelConfigService;

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
import com.feng.dsagent.modelconfig.dto.UpdateModelConfigDTO;
import com.feng.dsagent.modelconfig.vo.ModelConfigVO;

/**
 * 职责：管理端模型配置的查询、保存与连通性探测 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/admin/model-config")
@PreAuthorize("hasRole('ADMIN')")
public class ModelConfigController {

    private final ModelConfigService service;

    public ModelConfigController(ModelConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ConfigCapabilityVO<ModelConfigVO> capability() {
        return service.capability();
    }

    @PutMapping
    public ModelConfigVO update(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @Valid @RequestBody UpdateModelConfigDTO request,
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
