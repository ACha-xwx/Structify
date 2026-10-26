package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;

import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;
import com.feng.dsagent.studyroom.generation.service.GenerationCallService;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FragmentExecutionBudgetIntegrationTest {
    @Test
    void cachedCallsAreFreeButGenerationRepairAndReviewShareTheSameCap() throws Exception {
        var db = new FragmentTestDatabase();
        db.unit("u", 0);
        db.fragment("f", "u", 0, "PENDING");
        db.scheduler().reconcile(db.user, "run");
        var job = db.jobs.claim(Instant.now());
        String id = (String) job.get("id");
        var calls = new AtomicInteger();
        AiQuotaExecution model = (u, op, key, request) -> {
            calls.incrementAndGet();
            return new ModelResponse("{}", 10L);
        };
        var execution = new GenerationCallService(db.mapper(GenerationCallMapper.class), db.txManager, model);
        execution.complete(db.user, id, "generate", "system", "prompt", 100);
        execution.complete(db.user, id, "generate", "system", "prompt", 100);
        execution.complete(db.user, id, "review", "system", "prompt", 100);
        execution.complete(db.user, id, "repair", "system", "prompt", 100);
        execution.complete(db.user, id, "review-repair", "system", "prompt", 100);
        assertThatThrownBy(() -> execution.complete(db.user, id, "fifth", "system", "prompt", 100))
                .hasMessageContaining("片段生成预算");
        assertThat(calls).hasValue(4);
        assertThat(db.jdbc.queryForObject(
                        "SELECT consumed_tokens FROM studyroom_fragment_versions WHERE fragment_id='f' AND revision=1",
                        Long.class))
                .isEqualTo(40);
        assertThat(db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM studyroom_generation_calls WHERE request_context_json IS NOT NULL",
                        Integer.class))
                .isEqualTo(4);
    }
}
