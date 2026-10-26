package com.feng.dsagent.compiler.entity;

import java.time.Instant;

public record SandboxConfigEntity(
    long id,
    String provider,
    String baseUrl,
    boolean enabled,
    String lastConnectionTestStatus,
    Instant lastConnectionTestedAt,
    Instant updatedAt
) {
}
