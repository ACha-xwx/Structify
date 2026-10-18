package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DuplicateKeyException;

class FragmentSchemaIntegrationTest {
    @Test void migratedStorageRejectsDuplicateFragmentsAndKeepsPublishedRevisions() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:fragment-schema-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users(email,password_hash) VALUES('fragment-schema@example.com','hash')");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email='fragment-schema@example.com'", Long.class);
        jdbc.update("INSERT INTO study_rooms(id,user_id,title,initial_question,summary) VALUES('room',?,'title','question','summary')", user);
        jdbc.update("INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES('draft',?,'q','STANDARD','CONFIRMED')", user);
        jdbc.update("INSERT INTO studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json) VALUES('run','room',?,'draft',1,1,'PREPARING','{}')", user);
        jdbc.update("INSERT INTO studyroom_units(id,run_id,unit_key,unit_order,status,unit_spec_json,content_hash) VALUES('unit','run','key',0,'PENDING','{}','hash')");
        String insert = "INSERT INTO studyroom_fragments(id,run_id,unit_id,fragment_key,fragment_order,plan_json,plan_hash,status) VALUES(?,'run','unit','key',0,'{}','hash','PENDING')";
        jdbc.update(insert, "first");
        assertThatThrownBy(() -> jdbc.update(insert, "duplicate")).isInstanceOf(DuplicateKeyException.class);
        jdbc.update("INSERT INTO studyroom_fragment_versions(fragment_id,revision,status,content_json) VALUES('first',1,'READY','published')");
        jdbc.update("INSERT INTO studyroom_fragment_versions(fragment_id,revision,status) VALUES('first',2,'FAILED')");
        assertThat(jdbc.queryForObject("SELECT content_json FROM studyroom_fragment_versions WHERE fragment_id='first' AND revision=1", String.class)).isEqualTo("published");
        assertThat(jdbc.queryForObject("SELECT state_version FROM studyroom_runs WHERE id='run'", Long.class)).isZero();
    }
}
