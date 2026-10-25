package com.feng.dsagent.auth;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository {
    public void save(String email, String purpose, String codeHash, Instant expiresAt);
    public Optional<Instant> latestCreatedAt(String email, String purpose);
    public Optional<VerificationCodeRecord> latestActive(String email, String purpose);
    public boolean incrementAttemptsIfActive(long id, Instant now, int maximumAttempts);
    public boolean consumeIfActive(long id, Instant now, int maximumAttempts);
}
