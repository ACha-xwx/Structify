package com.feng.dsagent.compiler.vo;

import java.time.Instant;

public record SandboxRuntimeStatusVO(
    boolean codeExecutionConfigured,
    String source,
    String provider,
    Instant checkedAt
) {}
