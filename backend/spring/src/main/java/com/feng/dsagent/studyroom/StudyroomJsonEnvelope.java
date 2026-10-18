package com.feng.dsagent.studyroom;

import java.util.ArrayDeque;

/** Removes transport wrappers, never changes fields or completes truncated teaching content. */
final class StudyroomJsonEnvelope {
    private StudyroomJsonEnvelope() {}

    static String clean(String raw) {
        if (raw == null) throw new IllegalArgumentException("模型未返回JSON");
        String value = raw.strip();
        if (value.startsWith("```")) {
            int newline = value.indexOf('\n');
            if (newline < 0
                    || !value.substring(0, newline).strip().matches("(?i)```(?:json)?")
                    || !value.endsWith("```")) throw new IllegalArgumentException("JSON围栏格式错误");
            value = value.substring(newline + 1, value.length() - 3).strip();
        }
        if (!value.startsWith("{")) throw new IllegalArgumentException("模型必须返回JSON对象");
        var stack = new ArrayDeque<Character>();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
                continue;
            }
            if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') stack.push(ch);
            else if (ch == '}' || ch == ']') {
                if (stack.isEmpty() || stack.pop() != (ch == '}' ? '{' : '['))
                    throw new IllegalArgumentException("JSON内部括号不匹配");
                if (stack.isEmpty()) {
                    String tail = value.substring(i + 1);
                    if (!tail.matches("[\\s}\\]]*"))
                        throw new IllegalArgumentException("完整JSON后包含其他内容");
                    return value.substring(0, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("JSON内容不完整，不能截断或补写教学内容");
    }
}
