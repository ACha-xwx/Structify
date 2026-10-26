package com.feng.dsagent.animation.controller;

import com.feng.dsagent.animation.vo.AnimationGenerationVO;
import com.feng.dsagent.animation.dto.AnimationGenerationCommandDTO;
import com.feng.dsagent.animation.vo.AnimationObservationVO;
import com.feng.dsagent.animation.support.DsvpConcurrencyLimiter;
import com.feng.dsagent.animation.vo.DsvpSimulationVO;
import com.feng.dsagent.animation.support.DsvpAnimationAdapter;
import com.feng.dsagent.animation.domain.DsvpEvidenceSource;

import com.feng.dsagent.animation.service.DsvpEvidenceService;

import com.feng.dsagent.animation.service.AnimationObservationService;

import com.feng.dsagent.animation.service.AnimationGenerationService;

import com.feng.dsagent.aiquota.support.AiQuotaRequestId;
import com.feng.dsagent.common.FeatureRateLimiter;
import com.feng.dsagent.common.error.CommonErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import com.feng.dsagent.animation.dto.GenerateAnimationDTO;
import com.feng.dsagent.animation.dto.ObservationDTO;

@RestController
@RequestMapping("/api/v1/animations")
public class AnimationController {

    private final AnimationGenerationService animations;
    private final AnimationObservationService observations;
    private final FeatureRateLimiter rateLimiter;
    private final DsvpAnimationAdapter dsvp;
    private final DsvpConcurrencyLimiter dsvpConcurrency;
    private final DsvpEvidenceService evidence;

    /** Compatibility constructor for direct callers that only need the response adapter. */
    public AnimationController(
        AnimationGenerationService animations,
        AnimationObservationService observations,
        FeatureRateLimiter rateLimiter,
        DsvpAnimationAdapter dsvp,
        DsvpConcurrencyLimiter dsvpConcurrency
    ) {
        this.animations = animations;
        this.observations = observations;
        this.rateLimiter = rateLimiter;
        this.dsvp = dsvp;
        this.dsvpConcurrency = dsvpConcurrency;
        this.evidence = null;
    }

    @Autowired
    public AnimationController(
        AnimationGenerationService animations,
        AnimationObservationService observations,
        FeatureRateLimiter rateLimiter,
        DsvpAnimationAdapter dsvp,
        DsvpConcurrencyLimiter dsvpConcurrency,
        DsvpEvidenceService evidence
    ) {
        this.animations = animations;
        this.observations = observations;
        this.rateLimiter = rateLimiter;
        this.dsvp = dsvp;
        this.dsvpConcurrency = dsvpConcurrency;
        this.evidence = evidence;
    }

    @PostMapping("/simulate")
    public DsvpSimulationVO simulate(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @RequestBody JsonNode request
    ) {
        // 动画模拟是本地确定性计算：不消耗模型配额、毫秒级完成，一节课的正常使用就会调用几十次。
        // 通用限流（30 次/10 分钟）会在课堂上把它掐死——学生看到的就是"动画根本没成功"（2026-09-24）。
        // 滥用防护由登录态 + 并发闸（DsvpConcurrencyLimiter）+ 引擎 6 秒超时承担。
        try (DsvpConcurrencyLimiter.Permit ignored = dsvpConcurrency.acquire()) {
            return evidence == null
                ? dsvp.adapt(request)
                : evidence.simulate(user.userId(), user.roles(), request, DsvpEvidenceSource.fromRequest(request));
        }
    }

    @PostMapping("/generate")
    public AnimationGenerationVO generate(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @Valid @RequestBody GenerateAnimationDTO request
    ) {
        Long userId = user == null ? null : user.userId();
        rateLimiter.check(
            "animation",
            userId,
            servletRequest.getRemoteAddr(),
            CommonErrorCode.MODEL_FEATURE_RATE_LIMITED
        );
        return animations.generate(
            new AnimationGenerationCommandDTO(request.prompt(), request.preferredType(), request.chapterId()),
            userId,
            AiQuotaRequestId.from(servletRequest)
        );
    }

    @PostMapping("/{id}/observations")
    public AnimationObservationVO observation(
        @AuthenticationPrincipal AuthenticatedUser user,
        @org.springframework.web.bind.annotation.PathVariable String id,
        @Valid @RequestBody ObservationDTO request
    ) {
        return observations.record(user.userId(), id, request.observation());
    }

}
