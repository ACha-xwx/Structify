package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class StudyroomRepositoryIntegrationTest {

    @Autowired
    private StudyroomRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private String chapterId;

    @BeforeEach
    void createOwnerAndChapter() {
        userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        chapterId = "studyroom-" + UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)", userId, UUID.randomUUID() + "@example.com", "hash");
        jdbc.update(
            "INSERT INTO chapters (id, chapter_number, title) VALUES (?, ?, ?)",
            chapterId,
            (int) Math.floorMod(System.nanoTime(), 1_000_000L),
            "测试章节"
        );
    }

    @AfterEach
    void removeOwnerAndChapter() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        jdbc.update("DELETE FROM chapters WHERE id = ?", chapterId);
    }

    @Test
    void restoresOrchestrationWithoutChangingLegacyRecords() {
        var old = step("旧步骤", 0, false, null);
        var scene = StudyroomSceneAdapter.legacy("目标", "讲解", List.of());
        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(scene);
        var modern = new StudyroomRecords.Step("new-step", "new", "explanation", "新步骤", "讲解", "[]", "[]", 1, false, null, Instant.now(), json);
        var room = room("标题", "摘要", old.id());
        repository.create(room, List.of(old, modern), List.of());
        var restored = repository.findOwned(room.id(), userId).orElseThrow().steps();
        assertThat(restored.getFirst().orchestrationJson()).isNull();
        assertThat(restored.get(1).orchestrationJson()).isEqualTo(json);
    }

    @Test
    void createsAggregateAndRestoresStepsAndMessagesInOrder() {
        StudyroomRecords.Step first = step("第一步", 0, false, null);
        StudyroomRecords.Step second = step("第二步", 1, false, null);
        StudyroomRecords.Room room = room("标题", "摘要", first.id());
        StudyroomRecords.Message user = message("user", "怎么理解链表？");
        StudyroomRecords.Message assistant = message("assistant", "先看节点结构。");

        repository.create(room, List.of(first, second), List.of(user, assistant));
        StudyroomRecords.Aggregate aggregate = repository.findOwned(room.id(), userId).orElseThrow();

        assertThat(aggregate.room().id()).isEqualTo(room.id());
        assertThat(aggregate.steps()).extracting(StudyroomRecords.Step::title).containsExactly("第一步", "第二步");
        assertThat(aggregate.messages()).extracting(StudyroomRecords.Message::role).containsExactly("user", "assistant");
        assertThat(repository.findOwned(room.id(), userId + 1)).isEmpty();
    }

    @Test
    void appendsSupplementAndUpdatesProgressOnlyForOwnedSteps() {
        StudyroomRecords.Room room = room("标题", "摘要", null);
        StudyroomRecords.Step initial = step("原步骤", 0, false, null);
        repository.create(room, List.of(initial), List.of(message("user", "问题"), message("assistant", "回答")));

        StudyroomRecords.Step supplement = step("补充步骤", 1, true, initial.id());
        repository.appendExchange(room.id(), userId, List.of(message("user", "再展开"), message("assistant", "补充说明")), List.of(supplement));

        Optional<StudyroomRecords.Room> updated = repository.updateProgress(userId, room.id(), supplement.id());
        assertThat(updated).isPresent();
        assertThat(updated.orElseThrow().currentStepId()).isEqualTo(supplement.id());
        assertThat(repository.updateProgress(userId, room.id(), UUID.randomUUID().toString())).isEmpty();
        assertThat(repository.updateProgress(userId + 1, room.id(), initial.id())).isEmpty();
        assertThat(repository.findOwned(room.id(), userId).orElseThrow().steps()).hasSize(2);
    }

    @Test
    void deletingOwnerCascadesStudyroomChildren() {
        StudyroomRecords.Room room = room("标题", "摘要", null);
        repository.create(room, List.of(step("步骤", 0, false, null)), List.of(message("user", "问题")));

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(repository.findOwned(room.id(), userId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps WHERE study_room_id = ?", Integer.class, room.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_room_messages WHERE study_room_id = ?", Integer.class, room.id())).isZero();
    }

    @Test
    void rejectsAnInitialStepPointerThatDoesNotBelongToTheRoom() {
        StudyroomRecords.Room room = room("标题", "摘要", UUID.randomUUID().toString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.create(
            room,
            List.of(step("实际步骤", 0, false, null)),
            List.of(message("user", "问题"))
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findOwned(room.id(), userId)).isEmpty();
    }

    private StudyroomRecords.Room room(String title, String summary, String currentStepId) {
        Instant now = Instant.now();
        return new StudyroomRecords.Room(UUID.randomUUID().toString(), userId, chapterId, title, "initial question", summary, currentStepId, 1, now, now);
    }

    private static StudyroomRecords.Step step(String title, int order, boolean supplemental, String parentStepId) {
        return new StudyroomRecords.Step(UUID.randomUUID().toString(), "key-" + order, "explanation", title, "讲解", "[]", "[]", order, supplemental, parentStepId, Instant.now());
    }

    private static StudyroomRecords.Message message(String role, String content) {
        return new StudyroomRecords.Message(UUID.randomUUID().toString(), role, content, "[]", null, Instant.now());
    }
}
