package com.feng.dsagent.learning;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLearningWorkbenchRepository implements LearningWorkbenchRepository {

    private static final int CANDIDATE_LIMIT = 8;

    private final JdbcTemplate jdbc;

    JdbcLearningWorkbenchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PersistedSceneCandidate> findLatestOwnedDsvpScenes(long userId) {
        return jdbc.query(
            """
            SELECT a.id AS record_id, a.chapter_id, a.payload_json, a.updated_at
            FROM animation_records a
            INNER JOIN dsvp_request_snapshots s
                ON s.animation_record_id = a.id
                AND s.protocol_version = 'dsvp/1.0'
            INNER JOIN chapters c
                ON c.id = a.chapter_id
                AND c.status = 'PUBLISHED'
            WHERE a.user_id = ?
              AND a.chapter_id IS NOT NULL
            ORDER BY s.created_at DESC, a.updated_at DESC, a.id DESC
            LIMIT ?
            """,
            (row, index) -> new PersistedSceneCandidate(
                row.getString("record_id"),
                row.getString("chapter_id"),
                row.getString("payload_json"),
                instant(row.getTimestamp("updated_at"))
            ),
            userId,
            CANDIDATE_LIMIT
        );
    }

    private java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
