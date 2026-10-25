package com.feng.dsagent.compiler;

public record SandboxRuntimeSettings(SandboxProvider provider, String baseUrl, boolean enabled) {
}
