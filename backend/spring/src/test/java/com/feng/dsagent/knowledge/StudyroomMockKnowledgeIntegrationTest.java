package com.feng.dsagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:studyroom-original;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "app.knowledge.auto-publish-local=false"
})
@ActiveProfiles("dev")
class StudyroomMockKnowledgeIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test void repeatableMigrationRemovesExistingMockAndDoesNotRecreateIt() {
        jdbc.update("INSERT INTO knowledge_chunks (id, chapter_id, title, content, source_path, review_status, license_scope) VALUES ('studyroom-dev-mock-old', '03-stack-queue', '[开发模拟]', '模拟栈', 'dev-mock/03/lesson', 'VERIFIED', 'CLASSROOM_ONLY')");
        var cleanup = new ResourceDatabasePopulator(new ClassPathResource("db/dev/R__seed_studyroom_mock_knowledge.sql"));
        cleanup.execute(dataSource);
        cleanup.execute(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunks WHERE source_path LIKE 'dev-mock/%'", Integer.class)).isZero();
    }
}

@SpringBootTest
@ActiveProfiles("verification")
class StudyroomMockKnowledgeProductionIsolationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void doesNotInstallDevelopmentMockKnowledgeOutsideDevProfile() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunks WHERE source_path LIKE 'dev-mock/%'", Integer.class)).isZero();
    }
}
