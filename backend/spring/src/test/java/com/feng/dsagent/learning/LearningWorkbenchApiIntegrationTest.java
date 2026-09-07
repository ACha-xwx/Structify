package com.feng.dsagent.learning;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.security.JwtTokenService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LearningWorkbenchApiIntegrationTest {

    private static final long USER_ID = 9311L;
    private static final long OTHER_USER_ID = 9312L;
    private static final String VALID_RECORD_ID = "workbench-scene-valid";
    private static final String MALFORMED_RECORD_ID = "workbench-scene-malformed";
    private static final String OTHER_RECORD_ID = "workbench-scene-other";

    private static final String VALID_DEFINITION = """
        {"animation":true,"type":"stack","title":"栈入栈演示","description":"保存的 DSVP 场景。","initial":[1,2],"steps":[{"op":"push","label":"压入 3","note":"将 3 压入栈顶。","value":3,"index":2}]}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService tokens;

    @BeforeEach
    void prepareUsers() {
        deleteRecord(VALID_RECORD_ID);
        deleteRecord(MALFORMED_RECORD_ID);
        deleteRecord(OTHER_RECORD_ID);
        deleteUser(USER_ID);
        deleteUser(OTHER_USER_ID);
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", USER_ID, "workbench-owner@example.com", "hash");
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", OTHER_USER_ID, "workbench-other@example.com", "hash");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/learning/workbench"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void restoresTheLatestValidOwnedDsvpSceneAndSkipsMalformedCandidates() throws Exception {
        insertScene(USER_ID, VALID_RECORD_ID, "dsvp-workbench-valid", "03-stack-queue", VALID_DEFINITION, "2026-09-02T10:00:00Z");
        insertScene(USER_ID, MALFORMED_RECORD_ID, "dsvp-workbench-malformed", "03-stack-queue", "{not-json", "2026-09-02T11:00:00Z");
        insertScene(OTHER_USER_ID, OTHER_RECORD_ID, "dsvp-workbench-other", "02-linear-list", VALID_DEFINITION, "2026-09-02T12:00:00Z");

        String token = tokens.issue(USER_ID, "workbench-owner@example.com", Set.of("STUDENT"));
        mockMvc.perform(get("/api/v1/learning/workbench")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentChapterId").value("03-stack-queue"))
            .andExpect(jsonPath("$.scene.recordId").value(VALID_RECORD_ID))
            .andExpect(jsonPath("$.scene.chapterId").value("03-stack-queue"))
            .andExpect(jsonPath("$.scene.definition.type").value("stack"))
            .andExpect(jsonPath("$.scene.definition.steps[0].op").value("push"))
            .andExpect(jsonPath("$.progress.chapters[?(@.chapterId == '03-stack-queue')].animationCount").value(org.hamcrest.Matchers.hasItem(2)));
    }

    @Test
    void omitsAnOnlyMalformedSceneInsteadOfPromotingItToTheWorkbench() throws Exception {
        insertScene(USER_ID, MALFORMED_RECORD_ID, "dsvp-workbench-malformed", "03-stack-queue", "{not-json", "2026-09-02T11:00:00Z");

        String token = tokens.issue(USER_ID, "workbench-owner@example.com", Set.of("STUDENT"));
        mockMvc.perform(get("/api/v1/learning/workbench")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentChapterId").value(nullValue()))
            .andExpect(jsonPath("$.scene").value(nullValue()));
    }

    private void insertScene(
        long userId,
        String recordId,
        String traceId,
        String chapterId,
        String payloadJson,
        String createdAt
    ) {
        Instant timestamp = Instant.parse(createdAt);
        jdbc.update(
            """
            INSERT INTO animation_records (id, user_id, chapter_id, animation_type, title, payload_json, created_at, updated_at)
            VALUES (?, ?, ?, 'stack', '保存场景', ?, ?, ?)
            """,
            recordId,
            userId,
            chapterId,
            payloadJson,
            Timestamp.from(timestamp),
            Timestamp.from(timestamp)
        );
        jdbc.update(
            """
            INSERT INTO dsvp_request_snapshots (
                id, animation_record_id, protocol_version, request_json, request_hash,
                source_type, source_ref, version_label, review_status, created_at, updated_at
            ) VALUES (?, ?, 'dsvp/1.0', '{}', ?, 'API', 'test/workbench', '1.0', 'UNREVIEWED', ?, ?)
            """,
            traceId,
            recordId,
            "a".repeat(64),
            Timestamp.from(timestamp),
            Timestamp.from(timestamp)
        );
    }

    private void deleteRecord(String recordId) {
        jdbc.update("DELETE FROM dsvp_request_snapshots WHERE animation_record_id = ?", recordId);
        jdbc.update("DELETE FROM animation_observations WHERE animation_record_id = ?", recordId);
        jdbc.update("DELETE FROM animation_records WHERE id = ?", recordId);
    }

    private void deleteUser(long userId) {
        jdbc.update("DELETE FROM learning_records WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
}
