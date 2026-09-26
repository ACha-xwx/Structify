package com.feng.dsagent.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Section structure of the reviewed textbook.
 *
 * The reviewed pages keep the book's section lines as ordinary body text (for example
 * {@code 8.3.4　二叉搜索树的节点删除}) rather than markdown headings, so the numbering that both the
 * textbook and the courseware share is only recoverable by reading the running text. This class turns
 * that text back into a section per page, which is what lets the classroom narrow the textbook to the
 * section a courseware page belongs to before it looks for anything.
 */
public final class TextbookSections {

    /**
     * A section line: an optional asterisk (the book marks optional sections that way), a number, then a
     * short title with no sentence punctuation.
     */
    private static final Pattern SECTION_LINE = Pattern.compile(
        "^\\s*\\*?\\s*(\\d+(?:\\.\\d+){0,3})[\\s\\u3000]+(\\S[^。！？；]{1,23})\\s*$"
    );
    /**
     * The running header the book prints at the top of a page, for example
     * {@code 1.1　数据结构的基础概念　· 3 ·}: it states the section the page belongs to together with the
     * printed page number, which makes it the most direct evidence of a page's section.
     */
    private static final Pattern RUNNING_HEADER = Pattern.compile(
        "^\\s*\\*?\\s*(\\d+(?:\\.\\d+){0,3})[\\s\\u3000]+(.{1,40}?)[\\s\\u3000]*·[\\s\\u3000]*\\d+[\\s\\u3000]*·?\\s*$"
    );
    private static final Pattern SPACING = Pattern.compile("[\\s\\u3000]+");
    /** Lines that start with a number but are captions or list items, never a section title. */
    private static final Set<String> NOT_A_SECTION = Set.of("图", "表", "例", "解", "注", "式", "答", "见");
    private static final int MIN_COMPONENTS = 2;

    private TextbookSections() {
    }

    public record Span(int offset, String section) {
    }

    /** Section span under which a piece of text sits, or null when the line is not a section title. */
    public static String line(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        Matcher header = RUNNING_HEADER.matcher(trimmed);
        if (header.matches()) {
            String fromHeader = titled(header.group(1), header.group(2));
            if (fromHeader != null) {
                return fromHeader;
            }
        }
        Matcher matcher = SECTION_LINE.matcher(trimmed);
        return matcher.matches() ? titled(matcher.group(1), matcher.group(2)) : null;
    }

    private static String titled(String number, String rawTitle) {
        String title = SPACING.matcher(rawTitle.strip()).replaceAll(" ").strip();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 23
            || NOT_A_SECTION.contains(title.substring(0, 1))
            || title.matches(".*[。！？；].*")) {
            return null;
        }
        return number + " " + title;
    }

    /** Every section title inside the text, in reading order. */
    public static List<Span> spans(String content) {
        List<Span> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        int offset = 0;
        for (String line : content.split("\n", -1)) {
            String section = line(line);
            if (section != null) {
                result.add(new Span(offset, section));
            }
            offset += line.length() + 1;
        }
        return result;
    }

    /** The section in effect at the end of the text, keeping {@code previous} when the text adds none. */
    public static String carried(String content, String previous) {
        List<Span> spans = spans(content);
        return spans.isEmpty() ? previous : spans.get(spans.size() - 1).section();
    }

    /** Numeric part of a section title, e.g. {@code 8.3.4} for {@code 8.3.4 二叉搜索树的节点删除}. */
    public static String number(String section) {
        if (section == null || section.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile("^(\\d+(?:\\.\\d+){0,3})").matcher(section.strip());
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * The enclosing section one level up, e.g. {@code 8.3} for {@code 8.3.4}. Returns an empty string when
     * the number is already at the level this method treats as the widest useful scope.
     */
    public static String parent(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) {
            return "";
        }
        String[] parts = sectionNumber.split("\\.");
        if (parts.length < MIN_COMPONENTS + 1) {
            return "";
        }
        StringBuilder widened = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length - 1; index++) {
            widened.append('.').append(parts[index]);
        }
        return widened.toString();
    }

    /** Chapter number of a section number, e.g. {@code 8} for {@code 8.3.4}. */
    public static String chapter(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) {
            return "";
        }
        int dot = sectionNumber.indexOf('.');
        return dot < 0 ? sectionNumber : sectionNumber.substring(0, dot);
    }

    /** True when {@code candidate} is the same section or sits inside it, e.g. 8.3 covers 8.3.4. */
    public static boolean under(String candidate, String scope) {
        if (candidate == null || scope == null || candidate.isBlank() || scope.isBlank()) {
            return false;
        }
        return candidate.equals(scope) || candidate.startsWith(scope + ".");
    }
}
