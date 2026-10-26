package com.feng.dsagent.compiler.support;

import org.springframework.stereotype.Component;

import com.feng.dsagent.compiler.domain.SandboxRuntimeSettings;
/** Configuration readiness only; never submits a program or performs a network probe. */
@Component
public class CodeExecutionReadiness {
    private final SandboxConfigRuntimeSettingsSource source;

    public CodeExecutionReadiness(SandboxConfigRuntimeSettingsSource source) {
        this.source = source;
    }

    public boolean available() {
        return source.current().filter(SandboxRuntimeSettings::enabled).isPresent();
    }
}
