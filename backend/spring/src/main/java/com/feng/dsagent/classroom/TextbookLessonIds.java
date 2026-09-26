package com.feng.dsagent.classroom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Lesson identity for a reviewed textbook source. A lesson is scoped to its chapter so two
 * chapters that happen to share a file name cannot collide.
 */
public final class TextbookLessonIds {

    private TextbookLessonIds() {
    }

    public static String of(String chapterId, String sourcePath) {
        try {
            String scoped = (chapterId == null ? "" : chapterId) + "\n" + (sourcePath == null ? "" : sourcePath);
            return "textbook-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scoped.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * Courseware keys are the lesson number inside the reviewed file name, e.g.
     * {@code textbook/08-01-查找-基本概念与线性表查找-已核验页264-270.md -> 08-01}.
     */
    public static String coursewareKey(String sourcePath) {
        if (sourcePath == null) {
            return "";
        }
        String name = sourcePath.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        int reviewed = name.indexOf("-已核验页");
        if (reviewed >= 0) {
            name = name.substring(0, reviewed);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{2}-\\d{2})").matcher(name);
        return matcher.find() ? matcher.group(1) : "";
    }
}
