package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.feng.dsagent.security.JwtTokenService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SandboxConfigApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService tokens;

    @BeforeEach
    void clearState() {
        jdbc.update("DELETE FROM sandbox_configurations");
        jdbc.update("DELETE FROM admin_audit_events");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void requiresAdminAndPersistsAProviderNeutralConfigurationWithoutSecrets() throws Exception {
        long adminId = seedUser("sandbox-admin@example.com", "ACTIVE", "STUDENT", "ADMIN");
        long studentId = seedUser("sandbox-student@example.com", "ACTIVE", "STUDENT");
        String adminToken = bearer(adminId, "STUDENT", "ADMIN");
        String studentToken = bearer(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/sandbox-config"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/sandbox-config").header("Authorization", studentToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/sandbox-config")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"provider":"PISTON","baseUrl":"http://127.0.0.1:8080","enabled":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("PISTON"))
            .andExpect(jsonPath("$.baseUrl").value("http://127.0.0.1:8080"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));

        mockMvc.perform(get("/api/v1/admin/sandbox-config").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.configuration.provider").value("PISTON"))
            .andExpect(jsonPath("$.runtime.codeExecutionConfigured").value(true))
            .andExpect(jsonPath("$.runtime.source").value("persisted"))
            .andExpect(jsonPath("$.runtime.provider").value("PISTON"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_configurations", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void rejectsUnsafeProductionUrlsAndDoesNotTestDisabledConfiguration() throws Exception {
        long adminId = seedUser("sandbox-validation@example.com", "ACTIVE", "ADMIN");
        String token = bearer(adminId, "ADMIN");

        mockMvc.perform(put("/api/v1/admin/sandbox-config")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"provider":"JUDGE0","baseUrl":"http://sandbox.example","enabled":true}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SANDBOX_CONFIG_URL_UNSAFE"));

        mockMvc.perform(put("/api/v1/admin/sandbox-config")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"provider":"JUDGE0","baseUrl":"http://127.0.0.1:8080","enabled":false}
                    """))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/sandbox-config").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").value("PERSISTED_CONFIGURATION_DISABLED"))
            .andExpect(jsonPath("$.runtime.codeExecutionConfigured").value(false))
            .andExpect(jsonPath("$.runtime.source").value("persisted"))
            .andExpect(jsonPath("$.runtime.provider").value("JUDGE0"));
        mockMvc.perform(post("/api/v1/admin/sandbox-config/test").header("Authorization", token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SANDBOX_CONFIG_DISABLED"));
    }

    private long seedUser(String email, String status, String... roles) {
        jdbc.update("INSERT INTO users (email, password_hash, status) VALUES (?, ?, ?)", email, "hash", status);
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, ?)", id, role);
        }
        return id;
    }

    private String bearer(long userId, String... roles) {
        return "Bearer " + tokens.issue(userId, "sandbox-" + userId + "@example.com", Set.of(roles));
    }
}
