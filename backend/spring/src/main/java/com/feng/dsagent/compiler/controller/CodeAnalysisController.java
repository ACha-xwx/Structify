package com.feng.dsagent.compiler.controller;

import com.feng.dsagent.compiler.CodeAnalysisResponse;
import com.feng.dsagent.compiler.CodeAnalysisRequest;

import com.feng.dsagent.compiler.service.CodeAnalysisService;

import com.feng.dsagent.aiquota.AiQuotaRequestId;
import com.feng.dsagent.common.ModelFeatureRateLimiter;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/code")
public class CodeAnalysisController {

    private final CodeAnalysisService analysis;
    private final ModelFeatureRateLimiter rateLimiter;

    public CodeAnalysisController(CodeAnalysisService analysis, ModelFeatureRateLimiter rateLimiter) {
        this.analysis = analysis;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/analyze")
    public CodeAnalysisResponse analyze(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @RequestBody CodeAnalysisRequest request
    ) {
        Long userId = user == null ? null : user.userId();
        rateLimiter.check("code-analysis", userId, servletRequest.getRemoteAddr());
        return analysis.analyze(request, userId, AiQuotaRequestId.from(servletRequest));
    }
}
