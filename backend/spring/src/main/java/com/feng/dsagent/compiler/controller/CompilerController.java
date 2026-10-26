package com.feng.dsagent.compiler.controller;

import com.feng.dsagent.compiler.support.CompilerConcurrencyLimiter;
import com.feng.dsagent.compiler.vo.RunCodeVO;
import com.feng.dsagent.compiler.dto.RunCodeDTO;

import com.feng.dsagent.compiler.service.CompilerService;

import com.feng.dsagent.compiler.error.CompilerErrorCode;
import com.feng.dsagent.common.WindowRateLimiter;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/code")
public final class CompilerController {

    /**
     * A whole class shares one egress address, and the concurrency gate now admits several runs at
     * once, so the window has to be generous enough that the gate is what throttles - not this.
     */
    public static final int REQUESTS_PER_MINUTE = 60;

    private final CompilerService compiler;
    private final Clock clock;
    private final CompilerConcurrencyLimiter concurrencyLimiter;
    private final WindowRateLimiter rateLimiter = new WindowRateLimiter(
        REQUESTS_PER_MINUTE,
        Duration.ofMinutes(1)
    );

    @Autowired
    public CompilerController(
        CompilerService compiler,
        Clock clock,
        CompilerConcurrencyLimiter concurrencyLimiter
    ) {
        this.compiler = compiler;
        this.clock = clock;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    public CompilerController(CompilerService compiler, Clock clock) {
        this(compiler, clock, new CompilerConcurrencyLimiter(4, 1));
    }

    @PostMapping("/runs")
    public RunCodeVO run(
        @RequestBody RunCodeDTO request,
        HttpServletRequest servletRequest,
        Authentication authentication
    ) {
        if (!rateLimiter.allow(clientKey(authentication, servletRequest), clock.instant())) {
            throw CompilerErrorCode.RATE_LIMITED.toException();
        }
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
            ? user.userId()
            : null;
        try (CompilerConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire(clientKey(authentication, servletRequest))) {
            return compiler.run(request, userId);
        }
    }

    /**
     * Compatibility route retained for existing clients. New callers must use
     * the plural, documented `/runs` resource path.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @PostMapping("/run")
    public RunCodeVO runDeprecated(
        @RequestBody RunCodeDTO request,
        HttpServletRequest servletRequest,
        Authentication authentication
    ) {
        return run(request, servletRequest, authentication);
    }

    private static String clientKey(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        String remoteAddress = request.getRemoteAddr();
        return "ip:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
    }
}
