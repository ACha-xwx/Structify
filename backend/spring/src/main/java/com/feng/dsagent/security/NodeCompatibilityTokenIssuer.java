package com.feng.dsagent.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Issues short-lived tokens for the narrowly scoped legacy Node adapter. */
@Component
public final class NodeCompatibilityTokenIssuer {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = URL_ENCODER.encodeToString(
        "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)
    );
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final boolean enabled;
    private final byte[] secret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NodeCompatibilityTokenIssuer(
        SecurityProperties properties,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.enabled = properties.nodeCompatEnabled();
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (!enabled) {
            this.secret = new byte[0];
            return;
        }
        String configuredSecret = properties.nodeCompatJwtSecret();
        if (configuredSecret == null || configuredSecret.length() < 32) {
            throw new IllegalArgumentException("NODE_COMPAT_JWT_SECRET must contain at least 32 characters when enabled");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean enabled() {
        return enabled;
    }

    public IssuedNodeCompatibilityToken issue(AuthenticatedUser user) {
        if (!enabled) {
            throw new IllegalStateException("Node compatibility tokens are disabled");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(TOKEN_TTL);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.userId());
        payload.put("email", user.email());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        try {
            String encodedPayload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String unsigned = HEADER + "." + encodedPayload;
            String token = unsigned + "." + URL_ENCODER.encodeToString(sign(unsigned));
            return new IssuedNodeCompatibilityToken(token, TOKEN_TTL);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create Node compatibility token", error);
        }
    }

    private byte[] sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedNodeCompatibilityToken(String token, Duration ttl) {
    }
}
