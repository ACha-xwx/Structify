package com.feng.dsagent.studyroom.generation.support;

import java.util.Map;

/** Worker-local lease identity fences subsequent calls after recovery or supersession. */
public final class GenerationLease {
    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<>();

    private GenerationLease() {}

    public static Map<String, Object> current() {
        return CURRENT.get();
    }

    public static void enter(Map<String, Object> job) {
        CURRENT.set(job);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
