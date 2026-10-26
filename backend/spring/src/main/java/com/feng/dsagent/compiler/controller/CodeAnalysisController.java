package com.feng.dsagent.compiler.controller;

import com.feng.dsagent.compiler.vo.CodeAnalysisVO;
import com.feng.dsagent.compiler.dto.CodeAnalysisDTO;

import com.feng.dsagent.compiler.service.CodeAnalysisService;

import com.feng.dsagent.aiquota.support.AiQuotaRequestId;
import com.feng.dsagent.common.FeatureRateLimiter;
import com.feng.dsagent.common.error.CommonErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
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
    private final FeatureRateLimiter rateLimiter;

    public CodeAnalysisController(CodeAnalysisService analysis, FeatureRateLimiter rateLimiter) {
        this.analysis = analysis;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/analyze")
    public CodeAnalysisVO analyze(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @RequestBody CodeAnalysisDTO request
    ) {
        Long userId = user == null ? null : user.userId();
        rateLimiter.check(
            "code-analysis",
            userId,
            servletRequest.getRemoteAddr(),
            CommonErrorCode.MODEL_FEATURE_RATE_LIMITED
        );
        return analysis.analyze(request, userId, AiQuotaRequestId.from(servletRequest));
    }
}
