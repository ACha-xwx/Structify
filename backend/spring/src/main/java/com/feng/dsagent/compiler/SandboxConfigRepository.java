package com.feng.dsagent.compiler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SandboxConfigRepository {

    static final long CONFIGURATION_ID = 1L;

    private final JdbcTemplate jdbc;

    SandboxConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StoredSandboxConfig> find() {
        return jdbc.query(
            """
                SELECT id, provider, base_url, enabled, last_test_status, last_tested_at, updated_at
                FROM sandbox_configurations WHERE id = ?
                """,
            (row, index) -> new StoredSandboxConfig(
                row.getLong("id"),
                row.getString("provider"),
                row.getString("base_url"),
                row.getBoolean("enabled"),
                row.getString("last_test_status"),
                instant(row.getTimestamp("last_tested_at")),
                instant(row.getTimestamp("updated_at"))
            ),
            CONFIGURATION_ID
        ).stream().findFirst();
    }

    StoredSandboxConfig save(SandboxProvider provider, String baseUrl, boolean enabled) {
        int updated = jdbc.update(
            """
                UPDATE sandbox_configurations
                SET provider = ?, base_url = ?, enabled = ?, last_test_status = NULL,
                    last_tested_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            provider.name(), baseUrl, enabled, CONFIGURATION_ID
        );
        if (updated == 0) {
            try {
                jdbc.update(
                    """
                        INSERT INTO sandbox_configurations (id, provider, base_url, enabled)
                        VALUES (?, ?, ?, ?)
                        """,
                    CONFIGURATION_ID, provider.name(), baseUrl, enabled
                );
            } catch (DuplicateKeyException ignored) {
                save(provider, baseUrl, enabled);
            }
        }
        return find().orElseThrow(() -> new IllegalStateException("Sandbox configuration was not persisted"));
    }

    StoredSandboxConfig recordConnectionTest(String status) {
        jdbc.update(
            """
                UPDATE sandbox_configurations
                SET last_test_status = ?, last_tested_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            status, CONFIGURATION_ID
        );
        return find().orElseThrow(() -> new IllegalStateException("Sandbox configuration was not persisted"));
    }

    void appendAuditEvent(
        long actorUserId,
        String action,
        String result,
        String requestId,
        String beforeSummary,
        String afterSummary
    ) {
        jdbc.update(
            """
                INSERT INTO admin_audit_events (
                    actor_user_id, action, target_type, target_id, result, request_id, before_summary, after_summary
                ) VALUES (?, ?, 'SANDBOX_CONFIG', '1', ?, ?, ?, ?)
                """,
            actorUserId, action, result, safe(requestId), beforeSummary, afterSummary
        );
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, Math.min(128, value.length()));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record StoredSandboxConfig(
        long id,
        String provider,
        String baseUrl,
        boolean enabled,
        String lastConnectionTestStatus,
        Instant lastConnectionTestedAt,
        Instant updatedAt
    ) {
    }
}
