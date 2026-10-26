package com.feng.dsagent.auth.support;

import com.feng.dsagent.auth.error.AuthErrorCode;
import java.util.Locale;

/** Centralizes username validation and the stable lookup key used by persistence. */
public final class UsernamePolicy {

    public static final String REGEX = "^[A-Za-z0-9_]{3,32}$";

    private UsernamePolicy() {
    }

    public static String normalizeOptional(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return normalizeRequired(username);
    }

    public static String normalizeRequired(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!normalized.matches(REGEX)) {
            throw AuthErrorCode.AUTH_USERNAME_INVALID.toException();
        }
        return normalized;
    }

    public static String lookupKey(String username) {
        return normalizeRequired(username).toLowerCase(Locale.ROOT);
    }
}
