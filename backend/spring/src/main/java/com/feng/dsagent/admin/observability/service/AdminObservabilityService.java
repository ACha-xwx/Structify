package com.feng.dsagent.admin.observability.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.admin.observability.mapper.AdminObservabilityMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 职责：聚合后台/学习室任务与费用报表供运维控制台使用。
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

    private final AdminObservabilityMapper observability;

    public AdminObservabilityService(AdminObservabilityMapper observability) {
        this.observability = observability;
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
            total += nullToZero(observability.countBackground(status, userId));
            merged.addAll(background(limit, status, userId));
        }
        if (source == null || source.equals("STUDYROOM")) {
            total += nullToZero(observability.countStudyroom(status, userId));
            merged.addAll(studyroom(limit, status, userId));
        }
        merged.sort(Comparator.comparing(JobItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        int from = Math.min(merged.size(), safePage * safeSize);
        int to = Math.min(merged.size(), from + safeSize);
        return new JobPage(List.copyOf(merged.subList(from, to)), safePage, safeSize, total);
    }

    private List<JobItem> background(int limit, String status, Long userId) {
        return observability.selectBackground(blankToNull(status), userId, limit).stream()
            .map(row -> new JobItem(
                "BACKGROUND",
                String.valueOf(row.id()),
                row.taskType(),
                row.requestedByUserId(),
                row.status(),
                timestamp(row.createdAt()),
                timestamp(row.finishedAt()),
                row.failureReason(),
                0
            ))
            .toList();
    }

    private List<JobItem> studyroom(int limit, String status, Long userId) {
        return observability.selectStudyroom(blankToNull(status), userId, limit).stream()
            .map(row -> new JobItem(
                "STUDYROOM",
                row.id(),
                row.jobType(),
                row.userId(),
                row.status(),
                timestamp(row.createdAt()),
                timestamp(row.updatedAt()),
                row.failureMessage(),
                row.tokens()
            ))
            .toList();
    }

    /**
     * Tokens actually spent between two days, grouped by day or by user.
     *
     * <p>Only settled reservations count: a reservation that never completed is a reservation, not a cost.
     */
    public CostReport cost(LocalDate from, LocalDate to, String groupBy) {
        String grouping = "USER".equalsIgnoreCase(groupBy) ? "USER" : "DAY";
        List<AdminObservabilityMapper.CostRawRow> rows = observability.selectCostRows(from, to);

        Map<String, long[]> perDay = new LinkedHashMap<>();
        Map<Long, long[]> perUser = new LinkedHashMap<>();
        long calls = 0;
        long tokens = 0;
        for (AdminObservabilityMapper.CostRawRow row : rows) {
            long rowCalls = row.calls();
            long rowTokens = row.tokens() == null ? 0 : row.tokens();
            calls += rowCalls;
            tokens += rowTokens;
            perDay.computeIfAbsent(String.valueOf(row.quotaDate()), key -> new long[2]);
            long[] day = perDay.get(String.valueOf(row.quotaDate()));
            day[0] += rowCalls;
            day[1] += rowTokens;
            Long userId = row.userId();
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
        studyroom.put("rooms", nullToZero(observability.countStudyRooms()));
        studyroom.put("runs", nullToZero(observability.countStudyroomRuns()));
        studyroom.put("jobs", nullToZero(observability.countStudyroomJobs()));
        studyroom.put("queued", nullToZero(observability.countStudyroomQueued()));
        studyroom.put("failed", nullToZero(observability.countStudyroomFailed()));
        studyroom.put("fragments", nullToZero(observability.countFragments()));

        Map<String, Long> learning = new LinkedHashMap<>();
        learning.put("classroomEvents", nullToZero(observability.countClassroomEvents()));
        learning.put("codeRuns", nullToZero(observability.countCodeRuns()));
        learning.put("animationRecords", nullToZero(observability.countAnimationRecords()));
        learning.put("learners", nullToZero(observability.countLearners()));

        Map<String, Long> quota = new LinkedHashMap<>();
        quota.put("reservationsToday", nullToZero(observability.countReservationsToday()));
        quota.put("tokensToday", nullToZero(observability.sumTokensToday()));

        return List.of(
            new ModuleState("studyroom", true, "AVAILABLE", null, studyroom),
            new ModuleState("learning", true, "AVAILABLE", null, learning),
            new ModuleState("quota", true, "AVAILABLE", null, quota)
        );
    }

    private static String blankToNull(String status) {
        return status == null || status.isBlank() ? null : status;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
