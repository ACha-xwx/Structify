package com.feng.dsagent.knowledge.controller;

import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import com.feng.dsagent.knowledge.error.KnowledgeErrorCode;
import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.knowledge.vo.KnowledgeResultVO;
import com.feng.dsagent.knowledge.vo.KnowledgeSearchVO;
import com.feng.dsagent.knowledge.vo.KnowledgeSearchResultVO;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.nio.file.Path;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public final class KnowledgeController {

    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_CHAPTER_LENGTH = 64;
    private static final int DEFAULT_LIMIT = 4;
    private static final int MAX_LIMIT = 6;
    private static final int MAX_EXCERPT_LENGTH = 360;

    private final KnowledgeSearchService search;

    public KnowledgeController(KnowledgeSearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public KnowledgeSearchVO search(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String chapterId,
        @RequestParam(required = false, defaultValue = "4") String limit
    ) {
        String query = normalize(q);
        if (query.isBlank()) {
            throw KnowledgeErrorCode.QUERY_REQUIRED.toException();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw KnowledgeErrorCode.QUERY_TOO_LONG.toException();
        }
        String chapter = normalize(chapterId);
        if (chapter.length() > MAX_CHAPTER_LENGTH) {
            throw KnowledgeErrorCode.CHAPTER_TOO_LONG.toException();
        }
        int boundedLimit = parseLimit(limit);
        KnowledgeAudience audience = KnowledgeAudience.from(user);
        List<KnowledgeResultVO> results = search.search(query, chapter, boundedLimit, audience).stream()
            .map(KnowledgeController::view)
            .toList();
        return new KnowledgeSearchVO(true, query, results);
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            return Math.max(1, Math.min(MAX_LIMIT, Integer.parseInt(value.trim())));
        } catch (NumberFormatException error) {
            throw KnowledgeErrorCode.LIMIT_INVALID.toException();
        }
    }

    private static KnowledgeResultVO view(KnowledgeSearchResultVO result) {
        String source = safeSource(result.source());
        String location = blankToDefault(result.pageLabel(), result.chapterId());
        String kind = source.toLowerCase(java.util.Locale.ROOT).contains("answer") ? "answer" : "textbook";
        return new KnowledgeResultVO(
            result.id(),
            result.chapterId(),
            result.title(),
            result.chapterId(),
            kind,
            source,
            result.pageLabel(),
            source,
            location,
            "已审核",
            "PUBLISHED",
            truncate(result.content()),
            result.score()
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String value) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_EXCERPT_LENGTH
            ? compact
            : compact.substring(0, MAX_EXCERPT_LENGTH).trim() + "…";
    }

    private static String safeSource(String value) {
        String source = value == null ? "" : value.replace('\\', '/').trim();
        if (source.isBlank()) return "课程资料";
        if (source.contains("..") || source.startsWith("/") || source.matches("^[A-Za-z]:/.*")) {
            try {
                return Path.of(source).getFileName().toString();
            } catch (RuntimeException ignored) {
                return "课程资料";
            }
        }
        return source;
    }

}
