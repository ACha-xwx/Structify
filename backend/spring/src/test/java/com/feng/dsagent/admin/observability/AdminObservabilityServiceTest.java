package com.feng.dsagent.admin.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merged view is only worth having if it agrees with the ledgers it reads. These tests write one row in each
 * ledger and check that it shows up once, with the spend that belongs to it.
 */
@SpringBootTest
@Transactional
class AdminObservabilityServiceTest {

    @Autowired
    private AdminObservabilityService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private String jobId;

    @BeforeEach
    void seed() {
        jdbc.update(
            "INSERT INTO users (email, password_hash, status) VALUES (?, ?, 'ACTIVE')",
            "observability-" + UUID.randomUUID() + "@example.test",
            "x"
        );
        userId = jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);

        jdbc.update(
            "INSERT INTO background_tasks (task_type, status, requested_by_user_id) VALUES (?, ?, ?)",
            "STALE_TASK_RECOVERY", "SUCCEEDED", userId
        );

        jobId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO studyroom_generation_jobs (id, user_id, job_type, job_key, status, payload_json)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
            jobId, userId, "FRAGMENT", "job-" + jobId, "FAILED", "{}"
        );
        jdbc.update(
            "INSERT INTO studyroom_generation_calls (id, job_id, attempt, phase, status, quota_request_id, response_json)"
                + " VALUES (?, ?, 1, 'compose', 'SUCCEEDED', ?, '{}')",
            UUID.randomUUID().toString(), jobId, "req-" + jobId
        );
        // A reservation may only exist for a bucket: the quota ledger is keyed by (user, day).
        jdbc.update(
            "INSERT INTO ai_quota_buckets (user_id, quota_date, daily_token_quota, reserved_tokens, consumed_tokens)"
                + " VALUES (?, ?, 1000000, 0, 0)",
            userId, LocalDate.now()
        );
        jdbc.update(
            "INSERT INTO ai_quota_reservations"
                + " (id, user_id, quota_date, request_id, status, estimated_tokens, actual_tokens, created_at, expires_at, completed_at)"
                + " VALUES (?, ?, ?, ?, 'COMPLETED', 100, 640, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "res-" + jobId, userId, LocalDate.now(), "req-" + jobId
        );
    }

    @Test
    @DisplayName("both ledgers appear in one list, filtered by source when asked")
    void mergesBothLedgers() {
        AdminObservabilityService.JobPage all = service.jobs(null, null, userId, 0, 20);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).extracting(AdminObservabilityService.JobItem::source)
            .containsExactlyInAnyOrder("BACKGROUND", "STUDYROOM");

        AdminObservabilityService.JobPage studyroom = service.jobs("STUDYROOM", null, userId, 0, 20);
        assertThat(studyroom.total()).isEqualTo(1);
        assertThat(studyroom.items()).singleElement()
            .satisfies(item -> assertThat(item.type()).isEqualTo("FRAGMENT"));
    }

    @Test
    @DisplayName("a generation job carries the tokens its calls settled")
    void jobCarriesItsSpend() {
        AdminObservabilityService.JobItem job = service.jobs("STUDYROOM", null, userId, 0, 20).items().get(0);
        assertThat(job.tokens()).isEqualTo(640);
        assertThat(job.status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("cost counts settled calls only, and groups by day or by user")
    void costUsesSettledCalls() {
        LocalDate today = LocalDate.now();
        AdminObservabilityService.CostReport byDay = service.cost(today, today, "DAY");
        assertThat(byDay.tokens()).isGreaterThanOrEqualTo(640);
        assertThat(byDay.calls()).isGreaterThanOrEqualTo(1);

        AdminObservabilityService.CostReport byUser = service.cost(today, today, "USER");
        assertThat(byUser.groupBy()).isEqualTo("USER");
        assertThat(byUser.rows()).anySatisfy(row -> {
            if (row.userId() != null && row.userId() == userId) {
                assertThat(row.tokens()).isGreaterThanOrEqualTo(640);
            }
        });
    }

    @Test
    @DisplayName("the three modules the console never reported are reported, with counts")
    void reportsTheMissingModules() {
        List<AdminObservabilityService.ModuleState> modules = service.modules();
        assertThat(modules).extracting(AdminObservabilityService.ModuleState::module)
            .containsExactly("studyroom", "learning", "quota");

        Map<String, Long> studyroom = modules.get(0).counts();
        assertThat(studyroom.get("jobs")).isGreaterThanOrEqualTo(1);
        assertThat(studyroom.get("failed")).isGreaterThanOrEqualTo(1);
    }
}
