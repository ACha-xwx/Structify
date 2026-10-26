package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;

import com.feng.dsagent.studyroom.fragment.service.FragmentLearningService;
import org.junit.jupiter.api.Test;

class FragmentLearningServiceTest {
    @Test
    void retryIsExplicitIdempotentAndPreservesItsRevisionBudget() throws Exception {
        var db = new FragmentTestDatabase();
        db.unit("u", 0);
        db.fragment("f", "u", 0, "PENDING");
        var scheduler = db.scheduler();
        scheduler.reconcile(db.user, "run");
        db.jdbc.update("UPDATE studyroom_fragments SET status='FAILED' WHERE id='f'");
        db.jdbc.update("UPDATE studyroom_generation_jobs SET status='FAILED' WHERE fragment_id='f'");
        db.jdbc.update("UPDATE studyroom_fragment_versions SET consumed_calls=2 WHERE fragment_id='f'");
        var service = new FragmentLearningService(db.fragments, scheduler, db.json);
        var retry = service.retry(db.user, "run", "f", "key", null);
        assertThat(service.retry(db.user, "run", "f", "key", null)).isEqualTo(retry);
        assertThat(db.jdbc.queryForObject(
                        "SELECT consumed_calls FROM studyroom_fragment_versions WHERE fragment_id='f' AND revision=1",
                        Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> service.retry(db.user + 1, "run", "f", "unauthorized", null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.progress(db.user, "run", "u", "f", true, "progress"))
                .hasMessageContaining("片段尚未就绪");
    }
}
