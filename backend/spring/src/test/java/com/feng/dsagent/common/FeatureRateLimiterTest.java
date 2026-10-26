package com.feng.dsagent.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.error.CommonErrorCode;
import com.feng.dsagent.common.exception.BizException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FeatureRateLimiterTest {

    @Test
    void limitsGuestRequestsIndependentlyByFeature() {
        FeatureRateLimiter limiter = new FeatureRateLimiter(
            Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC)
        );

        for (int request = 0; request < 8; request++) {
            limiter.check("animation", null, "192.0.2.50", CommonErrorCode.MODEL_FEATURE_RATE_LIMITED);
        }
        limiter.check("code-analysis", null, "192.0.2.50", CommonErrorCode.MODEL_FEATURE_RATE_LIMITED);

        assertThatThrownBy(() ->
            limiter.check("animation", null, "192.0.2.50", CommonErrorCode.MODEL_FEATURE_RATE_LIMITED)
        )
            .isInstanceOfSatisfying(BizException.class, error ->
                org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("MODEL_FEATURE_RATE_LIMITED")
            );
    }

    @Test
    void limitsChatWithBlankPrefixSeparatelyFromFeatures() {
        FeatureRateLimiter limiter = new FeatureRateLimiter(
            Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC)
        );

        for (int request = 0; request < 8; request++) {
            limiter.check("", null, "192.0.2.50", CommonErrorCode.CHAT_RATE_LIMITED);
        }
        limiter.check("animation", null, "192.0.2.50", CommonErrorCode.MODEL_FEATURE_RATE_LIMITED);

        assertThatThrownBy(() ->
            limiter.check("", null, "192.0.2.50", CommonErrorCode.CHAT_RATE_LIMITED)
        )
            .isInstanceOfSatisfying(BizException.class, error ->
                org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("CHAT_RATE_LIMITED")
            );
    }
}
