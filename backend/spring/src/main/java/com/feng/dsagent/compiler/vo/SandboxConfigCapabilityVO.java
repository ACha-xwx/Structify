package com.feng.dsagent.compiler.vo;

public record SandboxConfigCapabilityVO(
    boolean available,
    String reason,
    SandboxConfigVO configuration,
    SandboxRuntimeStatusVO runtime
) {
    public SandboxConfigCapabilityVO(boolean available, String reason, SandboxConfigVO configuration) {
        this(available, reason, configuration, null);
    }
}
