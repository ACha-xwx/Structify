package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import com.feng.dsagent.studyroom.fragment.domain.FragmentPlan;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentLearningMapper;
import com.feng.dsagent.studyroom.fragment.worker.PreparationScheduler;
import com.feng.dsagent.studyroom.generation.mapper.GenerationJobMapper;
import com.feng.dsagent.studyroom.generation.service.GenerationJobService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

final class FragmentTestDatabase {
    final DriverManagerDataSource source = new DriverManagerDataSource(
        "jdbc:h2:mem:fragment-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    final JdbcTemplate jdbc = new JdbcTemplate(source);
    final OutlineJson json = new OutlineJson(JsonMapper.builder().build());
    final DataSourceTransactionManager txManager = new DataSourceTransactionManager(source);
    final SqlSessionTemplate sessions;
    final OutlineMapper store;
    final FragmentLearningMapper fragments;
    final GenerationJobMapper generationJobs;
    final GenerationJobService jobs;
    final long user;

    FragmentTestDatabase() throws Exception {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        sessions = StudyroomMapperFactory.template(source);
        store = sessions.getMapper(OutlineMapper.class);
        fragments = sessions.getMapper(FragmentLearningMapper.class);
        generationJobs = sessions.getMapper(GenerationJobMapper.class);
        jobs = new GenerationJobService(generationJobs, json, txManager);
        jdbc.update("INSERT INTO users(email,password_hash) VALUES('fragment@example.com','hash')");
        user = jdbc.queryForObject("SELECT id FROM users WHERE email='fragment@example.com'", Long.class);
        jdbc.update(
            "INSERT INTO study_rooms(id,user_id,title,initial_question,summary,current_run_id) VALUES('room',?,'title','question','summary','run')",
            user);
        jdbc.update(
            "INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES('draft',?,'q','STANDARD','CONFIRMED')",
            user);
        jdbc.update(
            "INSERT INTO studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json) VALUES('run','room',?,'draft',1,1,'PREPARING','{}')",
            user);
    }

    PreparationScheduler scheduler() {
        return new PreparationScheduler(fragments, jobs);
    }

    <T> T mapper(Class<T> type) {
        return sessions.getMapper(type);
    }

    void unit(String id, int order) {
        jdbc.update(
            "INSERT INTO studyroom_units(id,run_id,unit_key,unit_order,status,unit_spec_json,content_hash) VALUES(?,'run',?,?,'PENDING','{}','hash')",
            id,
            id,
            order);
    }

    void fragment(String id, String unit, int order, String status) {
        var plan = new FragmentPlan(
            id, order, "讲解栈", "CONCEPT", java.util.List.of("stack.definition"), java.util.List.of(), false);
        jdbc.update(
            "INSERT INTO studyroom_fragments(id,run_id,unit_id,fragment_key,fragment_order,plan_json,plan_hash,status,step_id) VALUES(?,'run',?,?,?,?,?,?,?)",
            id,
            unit,
            id,
            order,
            json.write(plan),
            "hash",
            status,
            status.equals("READY") ? "step-" + id : null);
    }
}
