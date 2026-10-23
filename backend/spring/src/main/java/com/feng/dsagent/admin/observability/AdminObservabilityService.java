package com.feng.dsagent.admin.observability;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only views over work the product is actually doing.
 *
 * <p>The admin console was written against the task ledger alone, but the studyroom keeps its own job tables and
 * its own quota accounting, so thirty-odd generation jobs could run without the console showing a single row.
 * These queries merge the sources rather than replacing them: nothing here writes, retries or cancels.
 */
@Service
public class AdminObservabilityService {

    /** A row of the merged job list. {@code source} says which ledger it came from. */
    public record JobItem(
        String source,
        String id,
        String type,
        Long ownerUserId,
        String status,
        String createdAt,
        String updatedAt,
        String error,
        long tokens
    ) {}

    public record JobPage(List<JobItem> items, int page, int size, long total) {}

    /** One line of the cost report: a day (or a user) with the calls and tokens it accounts for. */
    public record CostRow(String bucket, Long userId, long calls, long tokens) {}

    public record CostReport(String from, String to, String groupBy, long calls, long tokens, List<CostRow> rows) {}

    /** Module availability for the console home, with the counts an operator actually wants. */
    public record ModuleState(String module, boolean available, String status, String reason, Map<String, Long> counts) {}

    private static final int MAX_FETCH = 1000;

    private final JdbcTemplate jdbc;

    AdminObservabilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Both ledgers, newest first, in one page.
     *
     * <p>Each source is asked for its own newest {@code offset + size} rows; the merge happens here because a
     * portable SQL union would need casts that differ between MySQL and the in-memory database the tests use.
     */
    public JobPage jobs(String source, String status, Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        int limit = Math.min(MAX_FETCH, (safePage + 1) * safeSize);

        List<JobItem> merged = new ArrayList<>();
        long total = 0;
        if (source == null || source.equals("BACKGROUND")) {
            total += countBackground(status, userId);
            merged.addAll(background(limit, status, userId));
        }
        if (source == null || source.equals("STUDYROOM")) {
            total += countStudyroom(status, userId);
            merged.addAll(studyroom(limit, status, userId));
        }
        merged.sort(Comparator.comparing(JobItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        int from = Math.min(merged.size(), safePage * safeSize);
        int to = Math.min(merged.size(), from + safeSize);
        return new JobPage(List.copyOf(merged.subList(from, to)), safePage, safeSize, total);
    }

    private long countBackground(String status, Long userId) {
        List<Object> args = new ArrayList<>();
        String where = appendFilters("", status, userId, "requested_by_user_id", args);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM background_tasks WHERE " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private long countStudyroom(String status, Long userId) {
        List<Object> args = new ArrayList<>();
        String where = appendFilters("", status, userId, "user_id", args);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_generation_jobs WHERE " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** Note: a task ledger row can have no owner at all; the column is nullable on purpose. */
    private String appendFilters(String alias, String status, Long userId, String ownerColumn, List<Object> args) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (status != null && !status.isBlank()) {
            where.append(" AND ").append(alias).append("status = ?");
            args.add(status);
        }
        if (userId != null) {
            where.append(" AND ").append(alias).append(ownerColumn).append(" = ?");
            args.add(userId);
        }
        return where.toString();
    }

    private List<JobItem> background(int limit, String status, Long userId) {
        List<Object> args = new ArrayList<>();
        String where = appendFilters("", status, userId, "requested_by_user_id", args);
        args.add(limit);
        return jdbc.query(
            "SELECT id, task_type, status, created_at, started_at, finished_at, failure_reason, requested_by_user_id"
                + " FROM background_tasks WHERE " + where + " ORDER BY created_at DESC LIMIT ?",
            (row, index) -> new JobItem(
                "BACKGROUND",
                String.valueOf(row.getLong("id")),
                row.getString("task_type"),
                (Long) row.getObject("requested_by_user_id"),
                row.getString("status"),
                timestamp(row, "created_at"),
                timestamp(row, "finished_at"),
                row.getString("failure_reason"),
                0
            ),
            args.toArray()
        );
    }

    private List<JobItem> studyroom(int limit, String status, Long userId) {
        List<Object> args = new ArrayList<>();
        String where = appendFilters("j.", status, userId, "user_id", args);
        args.add(limit);
        // A job's spend lives in the quota ledger, reached through its recorded calls.
        String sql = "SELECT j.id, j.job_type, j.status, j.created_at, j.updated_at, j.failure_message, j.user_id,"
            + " (SELECT COALESCE(SUM(COALESCE(q.actual_tokens, q.estimated_tokens)), 0)"
            + "    FROM studyroom_generation_calls c"
            + "    JOIN ai_quota_reservations q ON q.request_id = c.quota_request_id"
            + "   WHERE c.job_id = j.id) AS tokens"
            + " FROM studyroom_generation_jobs j WHERE " + where
            + " ORDER BY j.created_at DESC LIMIT ?";
        return jdbc.query(sql, (row, index) -> new JobItem(
            "STUDYROOM",
            row.getString("id"),
            row.getString("job_type"),
            (Long) row.getObject("user_id"),
            row.getString("status"),
            timestamp(row, "created_at"),
            timestamp(row, "updated_at"),
            row.getString("failure_message"),
            row.getLong("tokens")
        ), args.toArray());
    }

    private static String timestamp(ResultSet row, String column) throws SQLException {
        java.sql.Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    /**
     * Tokens actually spent between two days, grouped by day or by user.
     *
     * <p>Only settled reservations count: a reservation that never completed is a reservation, not a cost.
     */
    public CostReport cost(LocalDate from, LocalDate to, String groupBy) {
        String grouping = "USER".equalsIgnoreCase(groupBy) ? "USER" : "DAY";
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT quota_date, user_id, COUNT(*) AS calls,"
                + " SUM(COALESCE(actual_tokens, estimated_tokens)) AS tokens"
                + " FROM ai_quota_reservations"
                + " WHERE completed_at IS NOT NULL AND quota_date BETWEEN ? AND ?"
                + " GROUP BY quota_date, user_id",
            from, to
        );

        Map<String, long[]> perDay = new LinkedHashMap<>();
        Map<Long, long[]> perUser = new LinkedHashMap<>();
        long calls = 0;
        long tokens = 0;
        for (Map<String, Object> row : rows) {
            long rowCalls = ((Number) row.get("calls")).longValue();
            long rowTokens = row.get("tokens") == null ? 0 : ((Number) row.get("tokens")).longValue();
            calls += rowCalls;
            tokens += rowTokens;
            perDay.computeIfAbsent(String.valueOf(row.get("quota_date")), key -> new long[2]);
            long[] day = perDay.get(String.valueOf(row.get("quota_date")));
            day[0] += rowCalls;
            day[1] += rowTokens;
            Long userId = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
            if (userId != null) {
                long[] user = perUser.computeIfAbsent(userId, key -> new long[2]);
                user[0] += rowCalls;
                user[1] += rowTokens;
            }
        }

        List<CostRow> lines = new ArrayList<>();
        if (grouping.equals("USER")) {
            perUser.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue()[1], left.getValue()[1]))
                .forEach(entry -> lines.add(new CostRow(null, entry.getKey(), entry.getValue()[0], entry.getValue()[1])));
        } else {
            perDay.entrySet().stream()
                .sorted((left, right) -> right.getKey().compareTo(left.getKey()))
                .forEach(entry -> lines.add(new CostRow(entry.getKey(), null, entry.getValue()[0], entry.getValue()[1])));
        }
        return new CostReport(from.toString(), to.toString(), grouping, calls, tokens, List.copyOf(lines));
    }

    /** The three modules the console never reported, with the numbers that say whether they are alive. */
    public List<ModuleState> modules() {
        Map<String, Long> studyroom = new LinkedHashMap<>();
        studyroom.put("rooms", count("SELECT COUNT(*) FROM study_rooms"));
        studyroom.put("runs", count("SELECT COUNT(*) FROM studyroom_runs"));
        studyroom.put("jobs", count("SELECT COUNT(*) FROM studyroom_generation_jobs"));
        studyroom.put("queued", count("SELECT COUNT(*) FROM studyroom_generation_jobs WHERE status IN ('QUEUED', 'RUNNING', 'LEASED')"));
        // A job can end in a failed state without ever recording a code, so both signals count.
        studyroom.put("failed", count(
            "SELECT COUNT(*) FROM studyroom_generation_jobs"
                + " WHERE failure_code IS NOT NULL OR UPPER(status) IN ('FAILED', 'DEAD', 'ERROR', 'CANCELED', 'CANCELLED')"
        ));
        studyroom.put("fragments", count("SELECT COUNT(*) FROM studyroom_fragments"));

        Map<String, Long> learning = new LinkedHashMap<>();
        learning.put("classroomEvents", count("SELECT COUNT(*) FROM classroom_events"));
        learning.put("codeRuns", count("SELECT COUNT(*) FROM code_runs"));
        learning.put("animationRecords", count("SELECT COUNT(*) FROM animation_records"));
        learning.put("learners", count(
            "SELECT COUNT(DISTINCT s.user_id) FROM classroom_events e JOIN classroom_sessions s ON s.id = e.session_id"
        ));

        Map<String, Long> quota = new LinkedHashMap<>();
        quota.put("reservationsToday", count("SELECT COUNT(*) FROM ai_quota_reservations WHERE quota_date = CURRENT_DATE"));
        quota.put("tokensToday", count("SELECT COALESCE(SUM(COALESCE(actual_tokens, estimated_tokens)), 0)"
            + " FROM ai_quota_reservations WHERE completed_at IS NOT NULL AND quota_date = CURRENT_DATE"));

        return List.of(
            new ModuleState("studyroom", true, "AVAILABLE", null, studyroom),
            new ModuleState("learning", true, "AVAILABLE", null, learning),
            new ModuleState("quota", true, "AVAILABLE", null, quota)
        );
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
