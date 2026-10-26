package com.feng.dsagent.compiler.vo;

import java.time.Instant;

public record SandboxConfigVO(
    String provider,
    String baseUrl,
    boolean enabled,
    String lastConnectionTestStatus,
    Instant lastConnectionTestedAt,
    Instant updatedAt
) {}
