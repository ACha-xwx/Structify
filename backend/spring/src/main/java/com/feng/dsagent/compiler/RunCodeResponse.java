package com.feng.dsagent.compiler;

import tools.jackson.databind.annotation.JsonSerialize;

public record RunCodeResponse(
    String language,
    String status,
    /** Program output travels ASCII-safe; see {@link AsciiSafeStringSerializer} for why. */
    @JsonSerialize(using = AsciiSafeStringSerializer.class) String stdout,
    @JsonSerialize(using = AsciiSafeStringSerializer.class) String stderr,
    long durationMs,
    String runId
) {
}
