package com.feng.dsagent.compiler;

import java.util.Locale;
import java.util.Optional;

enum SandboxProvider {
    PISTON,
    JUDGE0;

    static Optional<SandboxProvider> fromApiName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(value.strip().toUpperCase(Locale.ROOT)).map(SandboxProvider::valueOf);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
