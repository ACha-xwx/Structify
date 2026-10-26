package com.feng.dsagent.compiler.domain;

public record SandboxRuntimeSettings(SandboxProvider provider, String baseUrl, boolean enabled) {
}
