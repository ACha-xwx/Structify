package com.feng.dsagent.aiquota;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiQuotaLedgerRepository {

    private final JdbcTemplate jdbc;

    public JdbcAiQuotaLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureConcurrencyRow(long userId, Instant now) {
        try {
            jdbc.update(
                "INSERT INTO ai_quota_user_concurrency (user_id, active_reservations, updated_at) VALUES (?, 0, ?)",
                userId,
                Timestamp.from(now)
            );
        } catch (DuplicateKeyException ignored) {
            // Another request created this user's row. The following FOR UPDATE read serializes both requests.
        }
    }

    public AiQuotaConcurrency lockConcurrency(long userId) {
        return jdbc.query(
            "SELECT user_id, active_reservations FROM ai_quota_user_concurrency WHERE user_id = ? FOR UPDATE",
            (row, index) -> new AiQuotaConcurrency(row.getLong("user_id"), row.getInt("active_reservations")),
            userId
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("AI quota concurrency row was not created"));
    }

    public int activeReservations(long userId) {
        Integer active = jdbc.query(
            "SELECT active_reservations FROM ai_quota_user_concurrency WHERE user_id = ?",
            (row, index) -> row.getInt("active_reservations"),
            userId
        ).stream().findFirst().orElse(null);
        return active == null ? 0 : active;
    }

    public void ensureBucket(long userId, LocalDate quotaDate, long dailyTokenQuota, Instant now) {
        try {
            jdbc.update(
                """
                INSERT INTO ai_quota_buckets (
                    user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens, created_at, updated_at
                ) VALUES (?, ?, ?, 0, 0, ?, ?)
                """,
                userId,
                Date.valueOf(quotaDate),
                dailyTokenQuota,
                Timestamp.from(now),
                Timestamp.from(now)
            );
        } catch (DuplicateKeyException ignored) {
            // The row already exists and will be locked before it is used.
        }
    }

    public AiQuotaBucket lockBucket(long userId, LocalDate quotaDate, long currentDailyQuota, Instant now) {
        AiQuotaBucket bucket = jdbc.query(
            """
            SELECT user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens
            FROM ai_quota_buckets
            WHERE user_id = ? AND quota_date = ?
            FOR UPDATE
            """,
            (row, index) -> bucket(row),
            userId,
            Date.valueOf(quotaDate)
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("AI quota bucket was not created"));
        if (bucket.dailyTokenQuota() == currentDailyQuota) {
            return bucket;
        }
        jdbc.update(
            "UPDATE ai_quota_buckets SET daily_token_quota = ?, updated_at = ? WHERE user_id = ? AND quota_date = ?",
            currentDailyQuota,
            Timestamp.from(now),
            userId,
            Date.valueOf(quotaDate)
        );
        return new AiQuotaBucket(
            bucket.userId(),
            bucket.quotaDate(),
            currentDailyQuota,
            bucket.reservedTokens(),
            bucket.consumedTokens()
        );
    }

    public AiQuotaBucket lockExistingBucket(long userId, LocalDate quotaDate) {
        return jdbc.query(
            """
            SELECT user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens
            FROM ai_quota_buckets
            WHERE user_id = ? AND quota_date = ?
            FOR UPDATE
            """,
            (row, index) -> bucket(row),
            userId,
            Date.valueOf(quotaDate)
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("AI quota bucket is missing for reservation"));
    }

    public Optional<AiQuotaBucket> findBucket(long userId, LocalDate quotaDate) {
        return jdbc.query(
            """
            SELECT user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens
            FROM ai_quota_buckets
            WHERE user_id = ? AND quota_date = ?
            """,
            (row, index) -> bucket(row),
            userId,
            Date.valueOf(quotaDate)
        ).stream().findFirst();
    }

    public Optional<AiQuotaReservation> findReservation(String reservationId) {
        return jdbc.query(
            reservationSelect() + " WHERE id = ?",
            (row, index) -> reservation(row),
            reservationId
        ).stream().findFirst();
    }

    public Optional<AiQuotaReservation> lockReservation(String reservationId) {
        return jdbc.query(
            reservationSelect() + " WHERE id = ? FOR UPDATE",
            (row, index) -> reservation(row),
            reservationId
        ).stream().findFirst();
    }

    public Optional<AiQuotaReservation> findByRequestForUpdate(long userId, LocalDate quotaDate, String requestId) {
        return jdbc.query(
            reservationSelect() + " WHERE user_id = ? AND quota_date = ? AND request_id = ? FOR UPDATE",
            (row, index) -> reservation(row),
            userId,
            Date.valueOf(quotaDate),
            requestId
        ).stream().findFirst();
    }

    public List<String> expiredReservationIdsForUser(long userId, Instant now) {
        return jdbc.queryForList(
            """
            SELECT id FROM ai_quota_reservations
            WHERE user_id = ? AND status = 'RESERVED' AND expires_at <= ?
            ORDER BY expires_at ASC, id ASC
            """,
            String.class,
            userId,
            Timestamp.from(now)
        );
    }

    public List<String> expiredReservationIds(Instant now, int maximumCount) {
        return jdbc.queryForList(
            """
            SELECT id FROM ai_quota_reservations
            WHERE status = 'RESERVED' AND expires_at <= ?
            ORDER BY expires_at ASC, id ASC
            LIMIT ?
            """,
            String.class,
            Timestamp.from(now),
            maximumCount
        );
    }

    public void createReservation(AiQuotaReservation reservation) {
        jdbc.update(
            """
            INSERT INTO ai_quota_reservations (
                id, user_id, quota_date, request_id, status, estimated_tokens, actual_tokens,
                created_at, expires_at, completed_at, failure_code, usage_source
            ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, NULL, NULL)
            """,
            reservation.id(),
            reservation.userId(),
            Date.valueOf(reservation.quotaDate()),
            reservation.requestId(),
            reservation.status().name(),
            reservation.estimatedTokens(),
            Timestamp.from(reservation.createdAt()),
            Timestamp.from(reservation.expiresAt())
        );
    }

    public void reserveTokens(long userId, LocalDate quotaDate, long tokens, Instant now) {
        jdbc.update(
            """
            UPDATE ai_quota_buckets
            SET reserved_tokens = reserved_tokens + ?, updated_at = ?
            WHERE user_id = ? AND quota_date = ?
            """,
            tokens,
            Timestamp.from(now),
            userId,
            Date.valueOf(quotaDate)
        );
    }

    public void releaseAndCharge(long userId, LocalDate quotaDate, long reservedTokens, long actualTokens, Instant now) {
        jdbc.update(
            """
            UPDATE ai_quota_buckets
            SET reserved_tokens = reserved_tokens - ?, consumed_tokens = consumed_tokens + ?, updated_at = ?
            WHERE user_id = ? AND quota_date = ?
            """,
            reservedTokens,
            actualTokens,
            Timestamp.from(now),
            userId,
            Date.valueOf(quotaDate)
        );
    }

    public void charge(long userId, LocalDate quotaDate, long actualTokens, Instant now) {
        jdbc.update(
            """
            UPDATE ai_quota_buckets
            SET consumed_tokens = consumed_tokens + ?, updated_at = ?
            WHERE user_id = ? AND quota_date = ?
            """,
            actualTokens,
            Timestamp.from(now),
            userId,
            Date.valueOf(quotaDate)
        );
    }

    public void incrementConcurrency(long userId, Instant now) {
        jdbc.update(
            """
            UPDATE ai_quota_user_concurrency
            SET active_reservations = active_reservations + 1, updated_at = ?
            WHERE user_id = ?
            """,
            Timestamp.from(now),
            userId
        );
    }

    public void decrementConcurrency(long userId, Instant now) {
        jdbc.update(
            """
            UPDATE ai_quota_user_concurrency
            SET active_reservations = active_reservations - 1, updated_at = ?
            WHERE user_id = ?
            """,
            Timestamp.from(now),
            userId
        );
    }

    public boolean markSettled(String reservationId, long actualTokens, AiQuotaUsageSource usageSource, Instant now) {
        return jdbc.update(
            """
            UPDATE ai_quota_reservations
            SET status = 'SETTLED', actual_tokens = ?, completed_at = ?, failure_code = NULL, usage_source = ?
            WHERE id = ? AND status = 'RESERVED'
            """,
            actualTokens,
            Timestamp.from(now),
            usageSource.name(),
            reservationId
        ) == 1;
    }

    public boolean markExpiredSettlement(String reservationId, long actualTokens, AiQuotaUsageSource usageSource, Instant now) {
        return jdbc.update(
            """
            UPDATE ai_quota_reservations
            SET status = 'SETTLED', actual_tokens = ?, completed_at = ?, failure_code = NULL, usage_source = ?
            WHERE id = ? AND status = 'EXPIRED'
            """,
            actualTokens,
            Timestamp.from(now),
            usageSource.name(),
            reservationId
        ) == 1;
    }

    public boolean markFailed(
        String reservationId,
        long actualTokens,
        String failureCode,
        AiQuotaUsageSource usageSource,
        Instant now
    ) {
        return jdbc.update(
            """
            UPDATE ai_quota_reservations
            SET status = 'FAILED', actual_tokens = ?, completed_at = ?, failure_code = ?, usage_source = ?
            WHERE id = ? AND status = 'RESERVED'
            """,
            actualTokens,
            Timestamp.from(now),
            failureCode,
            usageSource.name(),
            reservationId
        ) == 1;
    }

    public boolean markExpiredFailure(
        String reservationId,
        long actualTokens,
        String failureCode,
        AiQuotaUsageSource usageSource,
        Instant now
    ) {
        return jdbc.update(
            """
            UPDATE ai_quota_reservations
            SET status = 'FAILED', actual_tokens = ?, completed_at = ?, failure_code = ?, usage_source = ?
            WHERE id = ? AND status = 'EXPIRED'
            """,
            actualTokens,
            Timestamp.from(now),
            failureCode,
            usageSource.name(),
            reservationId
        ) == 1;
    }

    public boolean markExpired(String reservationId, Instant now) {
        return jdbc.update(
            """
            UPDATE ai_quota_reservations
            SET status = 'EXPIRED', completed_at = ?, failure_code = 'AI_QUOTA_RESERVATION_EXPIRED',
                usage_source = 'NO_USAGE_REPORTED'
            WHERE id = ? AND status = 'RESERVED' AND expires_at <= ?
            """,
            Timestamp.from(now),
            reservationId,
            Timestamp.from(now)
        ) == 1;
    }

    private static AiQuotaBucket bucket(java.sql.ResultSet row) throws java.sql.SQLException {
        return new AiQuotaBucket(
            row.getLong("user_id"),
            row.getDate("quota_date").toLocalDate(),
            row.getLong("daily_token_quota"),
            row.getLong("reserved_tokens"),
            row.getLong("consumed_tokens")
        );
    }

    private static AiQuotaReservation reservation(java.sql.ResultSet row) throws java.sql.SQLException {
        long actualTokens = row.getLong("actual_tokens");
        return new AiQuotaReservation(
            row.getString("id"),
            row.getLong("user_id"),
            row.getDate("quota_date").toLocalDate(),
            row.getString("request_id"),
            AiQuotaReservationStatus.valueOf(row.getString("status")),
            row.getLong("estimated_tokens"),
            row.wasNull() ? null : actualTokens,
            instant(row.getTimestamp("created_at")),
            instant(row.getTimestamp("expires_at")),
            instant(row.getTimestamp("completed_at")),
            row.getString("failure_code"),
            usageSource(row.getString("usage_source"))
        );
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String reservationSelect() {
        return """
            SELECT id, user_id, quota_date, request_id, status, estimated_tokens, actual_tokens,
                   created_at, expires_at, completed_at, failure_code, usage_source
            FROM ai_quota_reservations
            """;
    }

    private static AiQuotaUsageSource usageSource(String value) {
        return value == null ? null : AiQuotaUsageSource.valueOf(value);
    }
}
