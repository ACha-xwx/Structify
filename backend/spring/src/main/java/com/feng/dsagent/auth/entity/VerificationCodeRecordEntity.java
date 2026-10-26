package com.feng.dsagent.auth.entity;

import java.time.Instant;

public record VerificationCodeRecordEntity(long id, String codeHash, int attempts, Instant expiresAt) {
}
