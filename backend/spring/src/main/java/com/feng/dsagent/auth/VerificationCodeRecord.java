package com.feng.dsagent.auth;

import java.time.Instant;

public record VerificationCodeRecord(long id, String codeHash, int attempts, Instant expiresAt) {
}
