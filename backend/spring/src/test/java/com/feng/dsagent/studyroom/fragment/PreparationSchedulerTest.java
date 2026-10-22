package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class PreparationSchedulerTest {
    @Test void preparesOnlyTheFocusAndOneAdjacentFragmentAndNeverImplicitlyRetries() {
        var db = new FragmentTestDatabase();
        for(int i=0;i<3;i++){db.unit("u"+i,i);db.fragment("f"+i,"u"+i,0,"PENDING");db.fragment("g"+i,"u"+i,1,"PENDING");}
        var jobs = new JdbcGenerationRepository(db.store,db.json,new DataSourceTransactionManager(db.source));
        var scheduler = new PreparationScheduler(new JdbcFragmentRepository(db.store,db.json),jobs,db.json);
        scheduler.reconcile(db.user,"run");
        assertThat(db.jdbc.queryForList("SELECT fragment_id FROM studyroom_generation_jobs WHERE status='QUEUED'",String.class))
            .containsExactlyInAnyOrder("f0","g0","f1");
        scheduler.requestFocus(db.user,"run","u2",1);
        assertThat(db.jdbc.queryForList("SELECT fragment_id FROM studyroom_generation_jobs WHERE status='QUEUED'",String.class))
            .containsExactlyInAnyOrder("f2","g2");
        scheduler.requestFocus(db.user,"run","u0",2);
        assertThat(db.jdbc.queryForList("SELECT fragment_id FROM studyroom_generation_jobs WHERE status='QUEUED'",String.class))
            .containsExactlyInAnyOrder("f0","g0","f1");
        scheduler.requestFocus(db.user,"run","u2",1);
        assertThat(db.jdbc.queryForObject("SELECT unit_id FROM studyroom_learning_progress WHERE run_id='run'",String.class)).isEqualTo("u0");
        db.jdbc.update("UPDATE studyroom_fragments SET status='FAILED' WHERE id='g0'");
        db.jdbc.update("UPDATE studyroom_generation_jobs SET status='FAILED' WHERE fragment_id='g0'");
        scheduler.reconcile(db.user,"run");
        assertThat(db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='g0'",String.class)).isEqualTo("FAILED");
    }
}
