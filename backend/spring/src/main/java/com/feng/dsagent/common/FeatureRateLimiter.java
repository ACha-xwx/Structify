package com.feng.dsagent.common;

import com.feng.dsagent.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Shared guest/authenticated window rate limiter for chat and model-backed features.
 * Blank {@code keyPrefix} yields the same keys as the former chat limiter ({@code guest:}/{@code user:});
 * a non-blank prefix scopes keys per feature ({@code animation:guest:}, etc.).
 */
@Component
public final class FeatureRateLimiter {

    private final WindowRateLimiter guest = new WindowRateLimiter(8, Duration.ofMinutes(10));
    private final WindowRateLimiter authenticated = new WindowRateLimiter(30, Duration.ofMinutes(10));
    private final Clock clock;

    public FeatureRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String keyPrefix, Long userId, String remoteAddress, ErrorCode rateLimitedCode) {
        Objects.requireNonNull(rateLimitedCode, "rateLimitedCode");
        String prefix = keyPrefix == null || keyPrefix.isBlank() ? "" : keyPrefix.trim() + ":";
        boolean allowed = userId == null
            ? guest.allow(prefix + "guest:" + safe(remoteAddress), clock.instant())
            : authenticated.allow(prefix + "user:" + userId, clock.instant());
        if (!allowed) {
            throw rateLimitedCode.toException();
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
