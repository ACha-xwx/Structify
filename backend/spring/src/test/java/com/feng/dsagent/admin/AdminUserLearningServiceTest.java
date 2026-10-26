package com.feng.dsagent.admin;

import com.feng.dsagent.admin.service.AdminUserLearningService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.exception.BizException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.feng.dsagent.admin.vo.AdminUserLearningVO;
import com.feng.dsagent.admin.vo.KnowledgeSettingsVO;

/**
 * The user learning view is a merge of half a dozen ledgers, so the test writes one row into each and checks the
 * numbers land where an administrator expects them. The audit row is asserted too: opening somebody's learning
 * profile must leave a trace, whoever opens it.
 */
@SpringBootTest
@Transactional
class AdminUserLearningServiceTest {

    @Autowired
    private AdminUserLearningService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long userId;

    @BeforeEach
    void seed() {
        adminId = user("learning-admin");
        userId = user("learner");
    }

    private long user(String name) {
        jdbc.update(
            "INSERT INTO users (email, password_hash, username, status) VALUES (?, ?, ?, 'ACTIVE')",
            name + "-" + UUID.randomUUID() + "@example.test", "x", name
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
    }

    @Test
    @DisplayName("an account with no history reports zeros and an empty map, not an error")
    void emptyAccountReadsAsZeros() {
        AdminUserLearningVO view = service.learning(userId, adminId, "req-empty");

        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.courses()).isEmpty();
        assertThat(view.bookmarks()).isZero();
        assertThat(view.classroomSessions()).isZero();
        assertThat(view.classroomEvents()).isZero();
        assertThat(view.lastActivityAt()).isNull();
        assertThat(view.tokensLast30Days()).isZero();
        assertThat(view.recentJobs()).isEmpty();
        assertThat(view.learningMap()).isNotNull();
    }

    @Test
    @DisplayName("classroom work, bookmarks, spend and jobs each show up with their numbers")
    void gathersEveryLedger() {
        // one chapter -> one script -> one session -> one event -> one bookmark
        String chapterId = "ch-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO chapters (id, chapter_number, title) VALUES (?, ?, ?)",
            chapterId, (int) (System.currentTimeMillis() % 900000) + 100000, "测试章"
        );
        String scriptId = "script-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO classroom_scripts (id, chapter_id, title, script_json) VALUES (?, ?, ?, '{}')",
            scriptId, chapterId, "测试脚本"
        );
        String sessionId = "session-" + UUID.randomUUID();
        jdbc.update(
            "INSERT INTO classroom_sessions (id, user_id, script_id, state) VALUES (?, ?, ?, 'STEP')",
            sessionId, userId, scriptId
        );
        jdbc.update(
            "INSERT INTO classroom_events (session_id, action, from_state, to_state) VALUES (?, 'ADVANCE', 'A', 'B')",
            sessionId
        );
        jdbc.update(
            "INSERT INTO classroom_bookmarks (user_id, session_id, step_index, title) VALUES (?, ?, 0, '回来看这里')",
            userId, sessionId
        );

        // one settled reservation: 640 tokens today
        jdbc.update(
            "INSERT INTO ai_quota_buckets (user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens)"
                + " VALUES (?, ?, 1000000, 0, 0)",
            userId, LocalDate.now()
        );
        jdbc.update(
            "INSERT INTO ai_quota_reservations"
                + " (id, user_id, quota_date, request_id, status, estimated_tokens, actual_tokens, created_at, expires_at, completed_at)"
                + " VALUES (?, ?, ?, ?, 'COMPLETED', 100, 640, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "res-" + UUID.randomUUID(), userId, LocalDate.now(), "req-" + UUID.randomUUID()
        );

        // one generation job
        String jobId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO studyroom_generation_jobs (id, user_id, job_type, job_key, status, payload_json)"
                + " VALUES (?, ?, 'FRAGMENT', ?, 'SUCCEEDED', '{}')",
            jobId, userId, "job-" + jobId
        );

        AdminUserLearningVO view = service.learning(userId, adminId, "req-full");

        assertThat(view.classroomSessions()).isEqualTo(1);
        assertThat(view.classroomEvents()).isEqualTo(1);
        assertThat(view.bookmarks()).isEqualTo(1);
        assertThat(view.lastActivityAt()).isNotNull();
        assertThat(view.tokensLast30Days()).isEqualTo(640);
        assertThat(view.callsLast30Days()).isEqualTo(1);
        assertThat(view.recentJobs()).singleElement()
            .satisfies(job -> {
                assertThat(job.id()).isEqualTo(jobId);
                assertThat(job.status()).isEqualTo("SUCCEEDED");
            });
    }

    @Test
    @DisplayName("opening the view writes one audit row naming the actor and the learner")
    void readIsAudited() {
        service.learning(userId, adminId, "req-audit");

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_events"
                + " WHERE action = 'ADMIN_VIEW_LEARNING_MAP' AND target_type = 'USER'"
                + " AND target_id = ? AND actor_user_id = ? AND request_id = 'req-audit'",
            Long.class, String.valueOf(userId), adminId
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown user is a named 404, not a database error")
    void unknownUserIsNamed() {
        assertThatThrownBy(() -> service.learning(999999999L, adminId, ""))
            .isInstanceOf(BizException.class)
            .satisfies(failure -> assertThat(((BizException) failure).code()).isEqualTo("USER_NOT_FOUND"));
    }
}
