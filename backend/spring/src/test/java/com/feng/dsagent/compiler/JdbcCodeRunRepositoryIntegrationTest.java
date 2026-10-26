package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.feng.dsagent.compiler.entity.CodeRunSnapshotEntity;
import com.feng.dsagent.compiler.mapper.CodeRunMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JdbcCodeRunRepositoryIntegrationTest {

    @Autowired
    private CodeRunMapper repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findOwnedReturnsOnlyTheRequestingUsersRun() {
        long ownerId = 8901L;
        long otherUserId = 8902L;
        jdbc.update(
            "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
            ownerId,
            "code-run-owner@example.com",
            "hash"
        );
        jdbc.update(
            "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
            otherUserId,
            "code-run-other@example.com",
            "hash"
        );

        String runId = UUID.randomUUID().toString();
        repository.insert(
            runId,
            ownerId,
            "03-stack-queue",
            "c",
            "int main(void) { return 0; }",
            "input",
            "success",
            "output",
            "",
            12L
        );

        assertThat(repository.selectOwned(runId, ownerId))
            .satisfies(run -> {
                assertThat(run).isNotNull();
                assertThat(run.id()).isEqualTo(runId);
                assertThat(run.chapterId()).isEqualTo("03-stack-queue");
                assertThat(run.language()).isEqualTo("c");
                assertThat(run.code()).isEqualTo("int main(void) { return 0; }");
                assertThat(run.stdin()).isEqualTo("input");
                assertThat(run.stdout()).isEqualTo("output");
                assertThat(run.stderr()).isEmpty();
                assertThat(run.status()).isEqualTo("success");
            });
        assertThat(repository.selectOwned(runId, otherUserId)).isNull();
        assertThat(repository.selectOwned("missing-run", ownerId)).isNull();
    }
}
