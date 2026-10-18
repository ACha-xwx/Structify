package com.feng.dsagent.studyroom.generation;

import java.util.Map;

/** Worker-local lease identity fences subsequent calls after recovery or supersession. */
public final class GenerationLease {
    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<>();

    private GenerationLease() {}

    public static Map<String, Object> current() {
        return CURRENT.get();
    }

    static void enter(Map<String, Object> job) {
        CURRENT.set(job);
    }

    static void clear() {
        CURRENT.remove();
    }
}
