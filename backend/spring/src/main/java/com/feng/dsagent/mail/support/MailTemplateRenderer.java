package com.feng.dsagent.mail.support;

import com.feng.dsagent.mail.error.MailErrorCode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MailTemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*}}")
;
    private static final Set<String> ALLOWED_VARIABLES = Set.of("site_name", "code", "expires_minutes");

    private MailTemplateRenderer() {
    }

    public static void validate(String subject, String html) {
        if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n")) {
            throw MailErrorCode.MAIL_SUBJECT_INVALID.toException("邮件主题不能为空且不能包含换行");
        }
        if (html == null || html.isBlank() || html.length() > 100_000) {
            throw MailErrorCode.MAIL_TEMPLATE_INVALID.toException("邮件 HTML 模板不能为空且长度不能超过 100000");
        }
        if (containsUnsafeMarkup(html)) {
            throw MailErrorCode.MAIL_TEMPLATE_UNSAFE_MARKUP.toException();
        }
        validateVariables(subject);
        validateVariables(html);
        if (!containsVariable(html, "code")) {
            throw MailErrorCode.MAIL_TEMPLATE_CODE_REQUIRED.toException();
        }
    }

    public static String render(String source, String siteName, String code, int expiresMinutes) {
        Matcher matcher = VARIABLE.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = switch (matcher.group(1)) {
                case "site_name" -> siteName;
                case "code" -> code;
                case "expires_minutes" -> Integer.toString(expiresMinutes);
                default -> matcher.group();
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String renderHtml(String source, String siteName, String code, int expiresMinutes) {
        return render(source, escape(siteName), escape(code), expiresMinutes);
    }

    private static void validateVariables(String value) {
        Matcher matcher = VARIABLE.matcher(value);
        while (matcher.find()) {
            if (!ALLOWED_VARIABLES.contains(matcher.group(1))) {
                throw MailErrorCode.MAIL_TEMPLATE_VARIABLE_INVALID.toException();
            }
        }
    }

    private static boolean containsVariable(String value, String variable) {
        Matcher matcher = VARIABLE.matcher(value);
        while (matcher.find()) {
            if (variable.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsafeMarkup(String html) {
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("<script") || lower.matches("(?s).*\\bon[a-z0-9_-]+\\s*=.*");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
