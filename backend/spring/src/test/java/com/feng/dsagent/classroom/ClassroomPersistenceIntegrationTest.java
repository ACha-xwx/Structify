package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import com.feng.dsagent.classroom.domain.ClassroomScript;
import com.feng.dsagent.classroom.domain.ClassroomState;
import com.feng.dsagent.classroom.entity.ClassroomSessionRecordEntity;
import com.feng.dsagent.classroom.mapper.ClassroomMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ClassroomPersistenceIntegrationTest {

    @Autowired
    private ClassroomMapper repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sessionKeepsTheScriptAndChapterSnapshotUsedAtCreation() {
        long userId = 8801L;
        String original = "{\"stages\":{\"OPENING\":{\"content\":\"原始课堂\"}}}";
        String changed = "{\"stages\":{\"OPENING\":{\"content\":\"后来修改\"}}}";
        jdbc.update(
                "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                userId,
                "classroom-snapshot@example.com",
                "hash");
        jdbc.update(
                """
            INSERT INTO classroom_scripts (
                id, chapter_id, title, version_label, review_status, script_json
            ) VALUES (?, '03-stack-queue', '快照课堂', '1.0', 'PUBLISHED', ?)
            """,
                "classroom-snapshot-script",
                original);
        ClassroomScript script = repository.findPublishedScript("classroom-snapshot-script");
        assertThat(script).isNotNull();
        String sessionId = UUID.randomUUID().toString();
        repository.insertSession(
                sessionId,
                userId,
                script.id(),
                ClassroomState.OPENING.name(),
                false,
                script.scriptJson(),
                script.chapterId());

        jdbc.update(
                "UPDATE classroom_scripts SET script_json = ?, chapter_id = '02-linear-list', version_label = '2.0' WHERE id = ?",
                changed,
                script.id());

        ClassroomSessionRecordEntity restored = repository.findSession(sessionId, userId);
        assertThat(restored.scriptJson()).isEqualTo(original);
        assertThat(restored.chapterId()).isEqualTo("03-stack-queue");
    }
}
