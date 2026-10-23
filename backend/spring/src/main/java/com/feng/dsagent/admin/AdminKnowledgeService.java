package com.feng.dsagent.admin;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import com.feng.dsagent.knowledge.KnowledgeEligibilityChanged;
import com.feng.dsagent.knowledge.KnowledgeEligibilitySql;
import com.feng.dsagent.knowledge.KnowledgeSearchResult;
import com.feng.dsagent.knowledge.KnowledgeSearchService;
import com.feng.dsagent.knowledge.KnowledgeSettingsService;
import com.feng.dsagent.knowledge.KnowledgeSettingsService.MatchingSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The knowledge-base console: chunk maintenance plus the retrieval tuning.
 *
 * <p>Two ground rules shape this service. First, every write publishes {@link KnowledgeEligibilityChanged}
 * inside its transaction so the retrieval snapshot is rebuilt after commit — nobody searches a stale snapshot.
 * Second, chunks under {@code textbook/}, {@code courseware/} and {@code dev-mock/} are owned by the import
 * pipeline and will be replaced on the next import; the console still lets an admin edit them (the next import
 * wins), while chunks created here live under {@code admin/} and are never touched by an import.
 */
@Service
public class AdminKnowledgeService {

    /** Source prefixes the import pipeline deletes and re-creates; manual edits there do not survive an import. */
    private static final List<String> IMPORT_OWNED_PREFIXES = List.of("textbook/", "courseware/", "dev-mock/");

    private static final List<String> REVIEW_STATUSES = List.of("DRAFT", "PENDING", "VERIFIED", "REJECTED");
    private static final List<String> LICENSE_SCOPES = List.of("PUBLIC", "CLASSROOM_ONLY", "TEAM_ONLY");
    private static final int TITLE_MAX = 300;
    private static final int PAGE_LABEL_MAX = 120;
    private static final int CONTENT_MAX = 50_000;

    public record KnowledgeOverview(
        long totalChunks,
        Map<String, Long> byReviewStatus,
        Map<String, Long> bySource,
        int snapshotChunks,
        boolean settingsCustomized
    ) {}

    public record KnowledgeSettingsView(
        double minimumScore,
        int searchLimit,
        double titleWeightLong,
        double titleWeightShort,
        boolean customized,
        MatchingSettings defaults
    ) {}

    public record ChunkRow(
        String id,
        String chapterId,
        String title,
        String sourcePath,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        boolean retrievable,
        boolean importOwned,
        int contentLength,
        Instant createdAt
    ) {}

    public record ChunkDetail(
        String id,
        String chapterId,
        String title,
        String content,
        String sourcePath,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        boolean retrievable,
        boolean importOwned,
        Instant createdAt
    ) {}

    public record TestSearchHit(
        String chunkId,
        String title,
        String sourcePath,
        String pageLabel,
        double score
    ) {}

    public record TestSearchResult(MatchingSettings effectiveSettings, List<TestSearchHit> hits) {}

    private final JdbcTemplate jdbc;
    private final KnowledgeSettingsService settings;
    private final KnowledgeSearchService search;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher events;

    public AdminKnowledgeService(
        JdbcTemplate jdbc,
        KnowledgeSettingsService settings,
        KnowledgeSearchService search,
        AdminAuditService audit,
        ApplicationEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.search = search;
        this.audit = audit;
        this.events = events;
    }

    // ----- overview & settings -------------------------------------------------

    KnowledgeOverview overview() {
        long total = countRows("SELECT COUNT(*) FROM knowledge_chunks", new Object[]{});
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query(
            "SELECT review_status, COUNT(*) AS n FROM knowledge_chunks GROUP BY review_status ORDER BY review_status",
            (row, index) -> new String[]{row.getString("review_status"), Long.toString(row.getLong("n"))}
        ).forEach(pair -> byStatus.put(pair[0], Long.valueOf(pair[1])));
        Map<String, Long> bySource = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT CASE
                     WHEN source_path LIKE 'textbook/%' THEN 'textbook'
                     WHEN source_path LIKE 'courseware/%' THEN 'courseware'
                     WHEN source_path LIKE 'dev-mock/%' THEN 'dev-mock'
                     WHEN source_path LIKE 'admin/%' THEN 'admin'
                     ELSE 'other'
                   END AS bucket,
                   COUNT(*) AS n
            FROM knowledge_chunks
            GROUP BY bucket
            ORDER BY bucket
            """,
            (row, index) -> new String[]{row.getString("bucket"), Long.toString(row.getLong("n"))}
        ).forEach(pair -> bySource.put(pair[0], Long.valueOf(pair[1])));
        return new KnowledgeOverview(total, byStatus, bySource, search.snapshotSize(), settings.customized());
    }

    KnowledgeSettingsView settingsView() {
        MatchingSettings current = settings.current();
        return new KnowledgeSettingsView(
            current.minimumScore(),
            current.searchLimit(),
            current.titleWeightLong(),
            current.titleWeightShort(),
            settings.customized(),
            settings.defaults()
        );
    }

    @Transactional
    KnowledgeSettingsView saveSettings(
        Double minimumScore,
        Integer searchLimit,
        Double titleWeightLong,
        Double titleWeightShort,
        long actorUserId,
        String requestId
    ) {
        if (minimumScore == null || searchLimit == null || titleWeightLong == null || titleWeightShort == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_SETTINGS_INCOMPLETE",
                "匹配方式的四个参数都必须填写"
            );
        }
        MatchingSettings next = new MatchingSettings(minimumScore, searchLimit, titleWeightLong, titleWeightShort);
        try {
            KnowledgeSettingsService.validate(next);
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_SETTINGS_INVALID",
                "匹配参数超出允许范围：分数 0-100、条数 1-12、标题权重 0.5-20"
            );
        }
        MatchingSettings before = settings.current();
        boolean wasCustomized = settings.customized();
        settings.save(next, actorUserId);
        audit.record(
            actorUserId,
            "KNOWLEDGE_SETTINGS_UPDATED",
            "KNOWLEDGE_SETTINGS",
            "1",
            "SUCCESS",
            requestId,
            (wasCustomized ? "customized;" : "defaults;") + settingsSummary(before),
            settingsSummary(next)
        );
        return settingsView();
    }

    @Transactional
    KnowledgeSettingsView resetSettings(long actorUserId, String requestId) {
        MatchingSettings before = settings.current();
        boolean removed = settings.resetToDefaults();
        audit.record(
            actorUserId,
            "KNOWLEDGE_SETTINGS_RESET",
            "KNOWLEDGE_SETTINGS",
            "1",
            removed ? "SUCCESS" : "NO_CHANGE",
            requestId,
            settingsSummary(before),
            removed ? "defaults" : settingsSummary(before)
        );
        return settingsView();
    }

    // ----- test search -----------------------------------------------------------

    TestSearchResult testSearch(
        String query,
        String chapterId,
        String audience,
        Double minimumScore,
        Integer searchLimit,
        Double titleWeightLong,
        Double titleWeightShort
    ) {
        if (query == null || query.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_QUERY_REQUIRED", "请输入要测试的检索词");
        }
        MatchingSettings base = settings.current();
        MatchingSettings effective = new MatchingSettings(
            minimumScore == null ? base.minimumScore() : minimumScore,
            searchLimit == null ? base.searchLimit() : searchLimit,
            titleWeightLong == null ? base.titleWeightLong() : titleWeightLong,
            titleWeightShort == null ? base.titleWeightShort() : titleWeightShort
        );
        try {
            KnowledgeSettingsService.validate(effective);
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_SETTINGS_INVALID",
                "匹配参数超出允许范围：分数 0-100、条数 1-12、标题权重 0.5-20"
            );
        }
        KnowledgeAudience resolvedAudience = parseAudience(audience);
        List<TestSearchHit> hits = search
            .preview(query, chapterId, effective.searchLimit(), resolvedAudience, effective)
            .stream()
            .map(AdminKnowledgeService::hit)
            .toList();
        return new TestSearchResult(effective, hits);
    }

    // ----- chunks -----------------------------------------------------------------

    AdminPage<ChunkRow> chunks(int page, int size, String chapterId, String status, String sourcePrefix, String q) {
        validatePage(page, size);
        String normalizedStatus = normalizeReviewStatus(status);
        String normalizedChapter = normalizeOptional(chapterId);
        String normalizedPrefix = normalizeOptional(sourcePrefix);
        String normalizedQuery = normalizeOptional(q);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (normalizedChapter != null) {
            where.append(" AND k.chapter_id = ?");
            params.add(normalizedChapter);
        }
        if (normalizedStatus != null) {
            where.append(" AND k.review_status = ?");
            params.add(normalizedStatus);
        }
        if (normalizedPrefix != null) {
            where.append(" AND k.source_path LIKE ?");
            params.add(normalizedPrefix.endsWith("/") ? normalizedPrefix + "%" : normalizedPrefix + "/%");
        }
        if (normalizedQuery != null) {
            where.append(" AND (k.title LIKE ? OR k.content LIKE ?)");
            String like = "%" + normalizedQuery + "%";
            params.add(like);
            params.add(like);
        }

        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM knowledge_chunks k" + where,
            Long.class,
            params.toArray()
        );
        String sql = """
            SELECT k.id, k.chapter_id, k.title, k.source_path, k.page_label, k.review_status, k.license_scope,
                   CASE WHEN %s THEN 1 ELSE 0 END AS retrievable,
                   CHAR_LENGTH(k.content) AS content_length, k.created_at
            FROM knowledge_chunks k
            LEFT JOIN chapters kc ON kc.id = k.chapter_id
            LEFT JOIN resources r ON r.id = k.resource_id
            LEFT JOIN chapters rc ON rc.id = r.chapter_id
            %s
            ORDER BY k.created_at DESC, k.id
            LIMIT ? OFFSET ?
            """.formatted(KnowledgeEligibilitySql.REVIEWED_SOURCE_CHAIN, where);
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(size);
        rowParams.add(page * size);
        List<ChunkRow> items = jdbc.query(sql, (row, index) -> new ChunkRow(
            row.getString("id"),
            row.getString("chapter_id"),
            row.getString("title"),
            row.getString("source_path"),
            row.getString("page_label"),
            row.getString("review_status"),
            row.getString("license_scope"),
            row.getInt("retrievable") == 1,
            importOwned(row.getString("source_path")),
            row.getInt("content_length"),
            timestamp(row.getTimestamp("created_at"))
        ), rowParams.toArray());
        return new AdminPage<>(items, page, size, total);
    }

    ChunkDetail chunk(String chunkId) {
        return requireChunk(chunkId);
    }

    @Transactional
    ChunkDetail createChunk(
        String chapterId,
        String title,
        String content,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        long actorUserId,
        String requestId
    ) {
        String cleanTitle = requireText(title, "标题", TITLE_MAX);
        String cleanContent = requireText(content, "正文", CONTENT_MAX);
        String cleanChapter = normalizeOptional(chapterId);
        if (cleanChapter != null) {
            requireChapter(cleanChapter);
        }
        String cleanLabel = normalizeOptional(pageLabel);
        if (cleanLabel != null && cleanLabel.length() > PAGE_LABEL_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_PAGE_LABEL_INVALID", "页签最长 120 个字符");
        }
        String status = reviewStatus == null ? "VERIFIED" : normalizeReviewStatus(reviewStatus);
        String scope = licenseScope == null ? "CLASSROOM_ONLY" : normalizeLicenseScope(licenseScope);

        String id = "admin-" + UUID.randomUUID();
        String sourcePath = "admin/manual/" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO knowledge_chunks (id, chapter_id, title, content, source_path, page_label, review_status, license_scope)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, cleanChapter, cleanTitle, cleanContent, sourcePath, cleanLabel, status, scope
        );
        events.publishEvent(new KnowledgeEligibilityChanged());
        ChunkDetail created = requireChunk(id);
        audit.record(
            actorUserId,
            "KNOWLEDGE_CHUNK_CREATED",
            "KNOWLEDGE_CHUNK",
            id,
            "SUCCESS",
            requestId,
            null,
            chunkSummary(created)
        );
        return created;
    }

    @Transactional
    ChunkDetail updateChunk(
        String chunkId,
        String chapterId,
        String title,
        String content,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        long actorUserId,
        String requestId
    ) {
        ChunkDetail before = requireChunk(chunkId);
        String cleanTitle = title == null ? before.title() : requireText(title, "标题", TITLE_MAX);
        String cleanContent = content == null ? before.content() : requireText(content, "正文", CONTENT_MAX);
        String cleanChapter = chapterId == null ? before.chapterId() : normalizeOptional(chapterId);
        if (cleanChapter != null && !cleanChapter.equals(before.chapterId())) {
            requireChapter(cleanChapter);
        }
        String cleanLabel = pageLabel == null ? before.pageLabel() : normalizeOptional(pageLabel);
        if (cleanLabel != null && cleanLabel.length() > PAGE_LABEL_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_PAGE_LABEL_INVALID", "页签最长 120 个字符");
        }
        String cleanStatus = reviewStatus == null ? before.reviewStatus() : normalizeReviewStatus(reviewStatus);
        String cleanScope = licenseScope == null ? before.licenseScope() : normalizeLicenseScope(licenseScope);

        if (cleanTitle.equals(before.title())
            && cleanContent.equals(before.content())
            && java.util.Objects.equals(cleanChapter, before.chapterId())
            && java.util.Objects.equals(cleanLabel, before.pageLabel())
            && cleanStatus.equals(before.reviewStatus())
            && cleanScope.equals(before.licenseScope())) {
            audit.record(
                actorUserId,
                "KNOWLEDGE_CHUNK_UPDATED",
                "KNOWLEDGE_CHUNK",
                chunkId,
                "NO_CHANGE",
                requestId,
                chunkSummary(before),
                chunkSummary(before)
            );
            return before;
        }

        jdbc.update(
            "UPDATE knowledge_chunks SET chapter_id = ?, title = ?, content = ?, page_label = ?,"
                + " review_status = ?, license_scope = ? WHERE id = ?",
            cleanChapter, cleanTitle, cleanContent, cleanLabel, cleanStatus, cleanScope, chunkId
        );
        events.publishEvent(new KnowledgeEligibilityChanged());
        ChunkDetail after = requireChunk(chunkId);
        audit.record(
            actorUserId,
            "KNOWLEDGE_CHUNK_UPDATED",
            "KNOWLEDGE_CHUNK",
            chunkId,
            "SUCCESS",
            requestId,
            chunkSummary(before),
            chunkSummary(after)
        );
        return after;
    }

    @Transactional
    void deleteChunk(String chunkId, long actorUserId, String requestId) {
        ChunkDetail before = requireChunk(chunkId);
        jdbc.update("DELETE FROM knowledge_chunks WHERE id = ?", chunkId);
        events.publishEvent(new KnowledgeEligibilityChanged());
        audit.record(
            actorUserId,
            "KNOWLEDGE_CHUNK_DELETED",
            "KNOWLEDGE_CHUNK",
            chunkId,
            "SUCCESS",
            requestId,
            chunkSummary(before),
            null
        );
    }

    // ----- helpers -----------------------------------------------------------------

    private ChunkDetail requireChunk(String chunkId) {
        String sql = """
            SELECT k.id, k.chapter_id, k.title, k.content, k.source_path, k.page_label, k.review_status,
                   k.license_scope, CASE WHEN %s THEN 1 ELSE 0 END AS retrievable, k.created_at
            FROM knowledge_chunks k
            LEFT JOIN chapters kc ON kc.id = k.chapter_id
            LEFT JOIN resources r ON r.id = k.resource_id
            LEFT JOIN chapters rc ON rc.id = r.chapter_id
            WHERE k.id = ?
            """.formatted(KnowledgeEligibilitySql.REVIEWED_SOURCE_CHAIN);
        List<ChunkDetail> rows = jdbc.query(sql, (row, index) -> new ChunkDetail(
            row.getString("id"),
            row.getString("chapter_id"),
            row.getString("title"),
            row.getString("content"),
            row.getString("source_path"),
            row.getString("page_label"),
            row.getString("review_status"),
            row.getString("license_scope"),
            row.getInt("retrievable") == 1,
            importOwned(row.getString("source_path")),
            timestamp(row.getTimestamp("created_at"))
        ), chunkId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_KNOWLEDGE_CHUNK_NOT_FOUND", "知识条目不存在");
        }
        return rows.get(0);
    }

    private void requireChapter(String chapterId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chapters WHERE id = ?",
            Integer.class,
            chapterId
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_CHAPTER_NOT_FOUND", "章节不存在");
        }
    }

    private long countRows(String sql, Object[] params) {
        Long value = jdbc.queryForObject(sql, Long.class, params);
        return value == null ? 0 : value;
    }

    private static TestSearchHit hit(KnowledgeSearchResult result) {
        return new TestSearchHit(
            result.chunk().id(),
            result.chunk().title(),
            result.chunk().source(),
            result.chunk().pageLabel(),
            result.score()
        );
    }

    private KnowledgeAudience parseAudience(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return KnowledgeAudience.TEAM;
        }
        try {
            return KnowledgeAudience.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_AUDIENCE_INVALID",
                "视角必须是 GUEST、STUDENT 或 TEAM"
            );
        }
    }

    private String normalizeReviewStatus(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        if (!REVIEW_STATUSES.contains(normalized)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_REVIEW_STATUS_INVALID",
                "审核状态必须是 DRAFT、PENDING、VERIFIED 或 REJECTED"
            );
        }
        return normalized;
    }

    private String normalizeLicenseScope(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        if (!LICENSE_SCOPES.contains(normalized)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_LICENSE_SCOPE_INVALID",
                "授权范围必须是 PUBLIC、CLASSROOM_ONLY 或 TEAM_ONLY"
            );
        }
        return normalized;
    }

    private static boolean importOwned(String sourcePath) {
        if (sourcePath == null) {
            return false;
        }
        for (String prefix : IMPORT_OWNED_PREFIXES) {
            if (sourcePath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_TEXT_REQUIRED", label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_KNOWLEDGE_TEXT_TOO_LONG",
                label + "最长 " + maxLength + " 个字符"
            );
        }
        return normalized;
    }

    private String settingsSummary(MatchingSettings s) {
        return "minimumScore=" + s.minimumScore()
            + ";searchLimit=" + s.searchLimit()
            + ";titleWeightLong=" + s.titleWeightLong()
            + ";titleWeightShort=" + s.titleWeightShort();
    }

    private String chunkSummary(ChunkDetail chunk) {
        return "title=" + chunk.title()
            + ";reviewStatus=" + chunk.reviewStatus()
            + ";licenseScope=" + chunk.licenseScope()
            + ";chapterId=" + (chunk.chapterId() == null ? "" : chunk.chapterId())
            + ";sourcePath=" + (chunk.sourcePath() == null ? "" : chunk.sourcePath());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_PAGE_INVALID", "分页参数无效");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
