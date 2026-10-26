package com.feng.dsagent.compiler.vo;

import tools.jackson.databind.annotation.JsonSerialize;

import com.feng.dsagent.compiler.support.AsciiSafeStringSerializer;
public record RunCodeVO(
    String language,
    String status,
    /** Program output travels ASCII-safe; see {@link AsciiSafeStringSerializer} for why. */
    @JsonSerialize(using = AsciiSafeStringSerializer.class) String stdout,
    @JsonSerialize(using = AsciiSafeStringSerializer.class) String stderr,
    long durationMs,
    String runId
) {
}
