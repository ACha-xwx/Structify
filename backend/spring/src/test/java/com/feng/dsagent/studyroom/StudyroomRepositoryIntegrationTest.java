package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;

import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.model.ModelStreamHandler;
import com.feng.dsagent.studyroom.converter.StudyroomRecordConverter;
import com.feng.dsagent.studyroom.entity.StudyroomRecordsEntity;
import com.feng.dsagent.studyroom.mapper.StudyroomMapper;
import com.feng.dsagent.studyroom.service.StudyroomService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;
import com.feng.dsagent.studyroom.support.StudyroomResourceCatalog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

class StudyroomRepositoryIntegrationTest {
    JdbcTemplate jdbc;
    StudyroomService rooms;
    long user;

    @BeforeEach
    void seed() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:room-repository-"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        StudyroomMapper mapper = StudyroomMapperFactory.create(source, StudyroomMapper.class);
        rooms = new StudyroomService(
                AiQuotaExecution.unmetered(new UnusedModel()),
                mapper,
                new StudyroomResourceCatalog(List.of()),
                new ObjectMapper(),
                Mappers.getMapper(StudyroomRecordConverter.class));
        jdbc.update("INSERT INTO users(email,password_hash) VALUES('repository@example.com','hash')");
        user = jdbc.queryForObject("SELECT id FROM users WHERE email='repository@example.com'", Long.class);
        jdbc.update(
                "INSERT INTO study_rooms(id,user_id,title,initial_question,summary,current_run_id) VALUES('room',?,'栈','栈是什么','定义','new-run')",
                user);
        jdbc.update(
                "INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,run_id,orchestration_json) VALUES('old','room','old','explanation','旧内容','old','[]','[]',0,'old-run','{}')");
        jdbc.update(
                "INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,run_id,orchestration_json) VALUES('new','room','new','explanation','当前内容','new','[]','[]',1,'new-run','{}')");
    }

    @Test
    void onlyReadsTheCurrentRunAndCannotSaveProgressToSupersededSteps() {
        assertThat(rooms.findOwned("room", user).orElseThrow().steps())
                .extracting(StudyroomRecordsEntity.Step::id)
                .containsExactly("new");
        assertThatThrownBy(() -> rooms.updateProgress(user, "room", "old")).isInstanceOf(BizException.class);
        assertThat(rooms.updateProgress(user, "room", "new").currentStepId()).isEqualTo("new");
        assertThat(rooms.findOwned("room", user + 99)).isEmpty();
    }

    @Test
    void appendsOnlyMessagesAndFencesStaleReplies() {
        var message = new StudyroomRecordsEntity.Message("message", "assistant", "回答", "[]", "new", Instant.now());
        assertThatThrownBy(() -> rooms.appendExchange("room", user, List.of(message), "old-run"))
                .isInstanceOf(BizException.class);
        rooms.appendExchange("room", user, List.of(message), "new-run");
        assertThat(rooms.findOwned("room", user).orElseThrow().messages())
                .extracting(StudyroomRecordsEntity.Message::id)
                .containsExactly("message");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps", Integer.class)).isEqualTo(2);
    }

    private static final class UnusedModel implements ModelClient {
        @Override
        public ModelResponse complete(ModelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(ModelRequest request, ModelStreamHandler handler) {
            throw new UnsupportedOperationException();
        }
    }
}
