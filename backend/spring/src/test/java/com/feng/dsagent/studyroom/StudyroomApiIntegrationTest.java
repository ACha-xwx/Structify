package com.feng.dsagent.studyroom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.security.JwtTokenService;
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
class StudyroomApiIntegrationTest {

    private static final long OWNER = 97101L;
    private static final long OTHER = 97102L;
    private static final String ROOM_ID = "studyroom-api-owned";
    private static final String STEP_ID = "studyroom-api-step";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService tokens;

    @BeforeEach
    void prepareData() {
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", OWNER, OTHER);
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", OWNER, "studyroom-owner@example.com", "hash");
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", OTHER, "studyroom-other@example.com", "hash");
        jdbc.update("INSERT INTO study_rooms (id, user_id, title, initial_question, summary, current_step_id, version) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ROOM_ID, OWNER, "链表基础", "链表是什么？", "学习节点结构", STEP_ID, 1);
        jdbc.update("INSERT INTO study_room_steps (id, study_room_id, step_key, step_type, title, explanation, evidence_json, resources_json, step_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            STEP_ID, ROOM_ID, "step-1", "explanation", "节点", "节点由数据和指针组成", "[]", "[]", 0);
        jdbc.update("INSERT INTO study_room_messages (id, study_room_id, message_order, role, content, sources_json, step_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "studyroom-api-message", ROOM_ID, 0, "assistant", "开始学习", "[]", STEP_ID);
        jdbc.update("INSERT INTO study_rooms (id, user_id, title, initial_question, summary, version) VALUES (?, ?, ?, ?, ?, ?)",
            "studyroom-api-foreign", OTHER, "他人课堂", "问题", "摘要", 1);
    }

    @Test
    void requiresLoginAndHidesForeignRooms() throws Exception {
        mockMvc.perform(get("/api/v1/study-rooms/" + ROOM_ID))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/study-rooms/" + ROOM_ID).header("Authorization", "Bearer " + token(OTHER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDYROOM_NOT_FOUND"));
    }

    @Test
    void readsOwnedStudyroomAndPersistsProgressWithoutChangingMessages() throws Exception {
        mockMvc.perform(get("/api/v1/study-rooms/" + ROOM_ID).header("Authorization", "Bearer " + token(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ROOM_ID))
            .andExpect(jsonPath("$.steps[0].explanation").value("节点由数据和指针组成"))
            .andExpect(jsonPath("$.messages[0].content").value("开始学习"));

        mockMvc.perform(patch("/api/v1/study-rooms/" + ROOM_ID + "/progress")
                .header("Authorization", "Bearer " + token(OWNER))
                .contentType("application/json")
                .content("{\"stepId\":\"" + STEP_ID + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStepId").value(STEP_ID))
            .andExpect(jsonPath("$.version").value(2));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM study_room_messages WHERE study_room_id = ?", Integer.class, ROOM_ID)).isEqualTo(1);
    }

    @Test
    void validatesQuestionAndProgressRequestBodies() throws Exception {
        mockMvc.perform(post("/api/v1/study-rooms/" + ROOM_ID + "/messages")
                .header("Authorization", "Bearer " + token(OWNER))
                .contentType("application/json")
                .content("{\"question\":\" \"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/study-rooms/" + ROOM_ID + "/progress")
                .header("Authorization", "Bearer " + token(OWNER))
                .contentType("application/json")
                .content("{\"stepId\":\"missing-step\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STUDYROOM_NOT_FOUND"));
    }

    private String token(long userId) {
        String email = userId == OWNER ? "studyroom-owner@example.com" : "studyroom-other@example.com";
        return tokens.issue(userId, email, Set.of("STUDENT"));
    }
}
