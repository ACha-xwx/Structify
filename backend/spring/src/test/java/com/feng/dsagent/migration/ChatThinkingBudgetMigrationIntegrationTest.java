package com.feng.dsagent.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChatThinkingBudgetMigrationIntegrationTest {

    @ParameterizedTest
    @CsvSource({
        "deepseek, deepseek-flash, 32768, 65536",
        "deepseek, deepseek-flash, 1024, 1024",
        "custom, deepseek-flash, 32768, 32768",
        "deepseek, another-model, 32768, 32768"
    })
    void expandsTheLegacyFlashCeilingWithoutChangingOtherControls(
        String provider, String model, int previousTokens, int expectedTokens
    ) {
        var dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:thinking-budget-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("29").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
            INSERT INTO model_configurations (id, provider, base_url, model_name, api_key_ciphertext,
                max_output_tokens, request_timeout_ms, daily_token_quota, enabled)
            VALUES (1, ?, 'https://provider.example/v1', ?, 'unchanged-encrypted-key', ?, 120000, 1000000, TRUE)
            """, provider, model, previousTokens);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT max_output_tokens FROM model_configurations WHERE id = 1", Integer.class))
            .isEqualTo(expectedTokens);
        assertThat(jdbc.queryForMap("""
            SELECT api_key_ciphertext, request_timeout_ms, daily_token_quota, enabled
            FROM model_configurations WHERE id = 1
            """))
            .containsEntry("api_key_ciphertext", "unchanged-encrypted-key")
            .containsEntry("request_timeout_ms", 120000L)
            .containsEntry("daily_token_quota", 1000000L)
            .containsEntry("enabled", true);
    }
}
