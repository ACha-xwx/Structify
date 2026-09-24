package com.feng.dsagent.compiler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the interactive sandbox - the one that keeps a learner's program alive between
 * keystrokes so the console can read like a terminal instead of a batch job.
 */
@ConfigurationProperties("app.sandbox")
public record SandboxProperties(
    boolean enabled,
    String gccCommand,
    String pythonCommand,
    int maximumSessions,
    int sessionIdleTimeoutSeconds,
    int sessionMaximumLifetimeSeconds,
    int compileTimeoutMillis,
    int maximumOutputLength,
    /**
     * Where a session's work directory is created. The system temporary directory is the default,
     * but a locked-down host mounts it `noexec`, which makes every compiled program unrunnable -
     * such a host points this at a volume that allows execution.
     */
    String workDirectory
) {

    public SandboxProperties {
        if (maximumSessions < 1 || maximumSessions > 512) {
            throw new IllegalArgumentException("sandbox session limit is invalid");
        }
        if (sessionIdleTimeoutSeconds < 5 || sessionMaximumLifetimeSeconds < sessionIdleTimeoutSeconds) {
            throw new IllegalArgumentException("sandbox session timeouts are invalid");
        }
        if (compileTimeoutMillis < 1_000 || compileTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("sandbox compile timeout is invalid");
        }
        if (maximumOutputLength < 1) {
            throw new IllegalArgumentException("sandbox output limit is invalid");
        }
    }

    String compilerFor(SupportedLanguage language) {
        return language == SupportedLanguage.C ? gccCommand : pythonCommand;
    }

    /** Null when the host did not name one, which means "use the system temporary directory". */
    java.nio.file.Path workDirectoryPathOrNull() {
        if (workDirectory == null || workDirectory.isBlank()) {
            return null;
        }
        return java.nio.file.Path.of(workDirectory.trim());
    }
}
