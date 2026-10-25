package com.feng.dsagent.studyroom.library;

import com.feng.dsagent.studyroom.library.mapper.CourseLibraryMapper;

import com.feng.dsagent.studyroom.library.service.CourseLibraryService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import tools.jackson.databind.json.JsonMapper;

class CourseLibraryServiceTest {
    JdbcTemplate db;
    CourseLibraryService library;
    JdbcOutlineRepository store;
    JdbcGenerationRepository jobs;
    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:library-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        db=new JdbcTemplate(source);store=new JdbcOutlineRepository(db);
        var json=new OutlineJson(JsonMapper.builder().build());
        library=new CourseLibraryService(StudyroomMapperFactory.create(source, CourseLibraryMapper.class),json);
        jobs=new JdbcGenerationRepository(store,json,new DataSourceTransactionManager(source));
        db.update("INSERT INTO users(id,email,password_hash) VALUES(101,'library@example.test','hash'),(102,'other@example.test','hash')");
        draft("draft",101);
    }
    void draft(String id,long owner) {db.update("INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES(?,?,'学习栈','STANDARD','GENERATING')",id,owner);}
    void confirm() {
        db.update("INSERT INTO study_rooms(id,user_id,title,initial_question,summary,current_run_id) VALUES('room',101,'栈与队列','q','s','run')");
        db.update("INSERT INTO studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json) VALUES('run','room',101,'draft',1,1,'PREPARING','{}')");
        db.update("UPDATE study_outline_drafts SET confirmed_run_id='run',source_room_id='room',status='CONFIRMED' WHERE id='draft'");
        db.update("INSERT INTO studyroom_units(id,run_id,unit_key,unit_order,status,unit_spec_json,content_hash) VALUES('unit','run','concept',0,'PENDING','{\"title\":\"栈的操作\"}','hash')");
        db.update("INSERT INTO studyroom_fragments(id,run_id,unit_id,fragment_key,fragment_order,plan_json,plan_hash,status) VALUES('fragment','run','unit','concept',0,'{}','hash','QUEUED')");
    }
    @Test void isolatesUsersAndPreservesTitleOnRename() {
        draft("other",102);
        assertThat(library.list(101)).extracting(CourseLibraryService.Course::id).containsExactly("draft");
        library.rename(101,"draft","  新课程名  ");
        assertThat(library.list(101).getFirst().title()).isEqualTo("新课程名");
        assertThat(db.queryForObject("SELECT question FROM study_outline_drafts WHERE id='draft'",String.class)).isEqualTo("学习栈");
        assertThatThrownBy(()->library.rename(102,"draft","x")).hasMessageContaining("不存在");
        assertThatThrownBy(()->library.rename(101,"draft","  ")).hasMessageContaining("名称");
        assertThatThrownBy(()->library.delete(102,"draft")).hasMessageContaining("不存在");
    }
    @Test void derivesPreparationLearningCompletionAndDoesNotSortByPublication() {
        confirm();
        var before=library.list(101).getFirst();
        assertThat(before.status()).isEqualTo("PREPARING");
        db.update("UPDATE studyroom_fragments SET step_id='step',status='READY' WHERE id='fragment'");
        db.update("UPDATE study_rooms SET updated_at=TIMESTAMP '2099-01-01 00:00:00' WHERE id='room'");
        assertThat(library.list(101).getFirst().activityAt()).isEqualTo(before.activityAt());
        assertThat(library.list(101).getFirst().status()).isEqualTo("LEARNING");
        db.update("INSERT INTO studyroom_learning_progress(user_id,run_id,unit_id,fragment_id) VALUES(101,'run','unit','fragment')");
        assertThat(library.list(101).getFirst().lastPointTitle()).isEqualTo("栈的操作");
        db.update("INSERT INTO studyroom_completed_knowledge(user_id,run_id,unit_id) VALUES(101,'run','unit')");
        assertThat(library.list(101).getFirst().status()).isEqualTo("COMPLETED");
    }
    @Test void deletionRevokesWorkerLeaseAndKeepsAccountingHistory() {
        confirm();
        String id=jobs.enqueue(101,"draft","run","unit","FRAGMENT","library-job",Map.of());
        var claimed=jobs.claim(Instant.now());assertThat(jobs.active(claimed)).isTrue();
        library.delete(101,"draft");library.delete(101,"draft");
        assertThat(library.list(101)).isEmpty();assertThat(jobs.active(claimed)).isFalse();
        assertThat(db.queryForObject("SELECT status FROM studyroom_generation_jobs WHERE id=?",String.class,id)).isEqualTo("CANCELLED");
        assertThat(db.queryForObject("SELECT current_run_id FROM study_rooms WHERE id='room'",String.class)).isNull();
        assertThatThrownBy(()->store.owned(101,"draft",false)).hasMessageContaining("不存在");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM studyroom_runs WHERE id='run'",Integer.class)).isEqualTo(1);
    }
    @Test void hidesSupersededConfirmedDraftsButKeepsUnconfirmedRevisions() {
        confirm();draft("revision",101);draft("superseded",101);
        db.update("INSERT INTO studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json) VALUES('old-run','room',101,'superseded',1,2,'SUPERSEDED','{}')");
        db.update("UPDATE study_outline_drafts SET confirmed_run_id='old-run',source_room_id='room' WHERE id='superseded'");
        db.update("UPDATE study_outline_drafts SET source_room_id='room' WHERE id='revision'");
        assertThat(library.list(101)).extracting(CourseLibraryService.Course::id).containsExactlyInAnyOrder("draft","revision");
        library.delete(101,"revision");assertThat(library.list(101)).extracting(CourseLibraryService.Course::id).containsExactly("draft");
    }
}
