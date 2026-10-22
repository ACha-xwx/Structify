package com.feng.dsagent.studyroom;
import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.common.ApiException;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
class StudyroomRepositoryIntegrationTest {
    JdbcTemplate jdbc;JdbcStudyroomRepository repository;long user;
    @BeforeEach void seed(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:room-repository-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(source);repository=new JdbcStudyroomRepository(jdbc);
        jdbc.update("INSERT INTO users(email,password_hash) VALUES('repository@example.com','hash')");
        user=jdbc.queryForObject("SELECT id FROM users WHERE email='repository@example.com'",Long.class);
        jdbc.update("INSERT INTO study_rooms(id,user_id,title,initial_question,summary,current_run_id) VALUES('room',?,'栈','栈是什么','定义','new-run')",user);
        jdbc.update("INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,run_id,orchestration_json) VALUES('old','room','old','explanation','旧内容','old','[]','[]',0,'old-run','{}')");
        jdbc.update("INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,run_id,orchestration_json) VALUES('new','room','new','explanation','当前内容','new','[]','[]',1,'new-run','{}')");
    }
    @Test void onlyReadsTheCurrentRunAndCannotSaveProgressToSupersededSteps(){
        assertThat(repository.findOwned("room",user).orElseThrow().steps()).extracting(StudyroomRecords.Step::id).containsExactly("new");
        assertThat(repository.updateProgress(user,"room","old")).isEmpty();
        assertThat(repository.updateProgress(user,"room","new")).isPresent();
        assertThat(repository.findOwned("room",user+99)).isEmpty();
    }
    @Test void appendsOnlyMessagesAndFencesStaleReplies(){
        var message=new StudyroomRecords.Message("message","assistant","回答","[]","new",Instant.now());
        assertThatThrownBy(()->repository.appendExchange("room",user,List.of(message),"old-run")).isInstanceOf(ApiException.class);
        repository.appendExchange("room",user,List.of(message),"new-run");
        assertThat(repository.findOwned("room",user).orElseThrow().messages()).extracting(StudyroomRecords.Message::id).containsExactly("message");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps",Integer.class)).isEqualTo(2);
    }
}
