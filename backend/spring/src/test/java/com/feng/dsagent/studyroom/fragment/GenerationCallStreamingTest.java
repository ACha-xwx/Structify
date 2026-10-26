package com.feng.dsagent.studyroom.fragment;

import static org.junit.jupiter.api.Assertions.*;

import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.model.ModelStreamHandler;
import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;
import com.feng.dsagent.studyroom.generation.service.GenerationCallService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GenerationCallStreamingTest {
    @Test
    void streamsContentOnceAndCachesWithProviderUsage() throws Exception {
        var db = new FragmentTestDatabase();
        db.unit("u", 0);
        db.fragment("f", "u", 0, "PENDING");
        db.scheduler().reconcile(db.user, "run");
        String job = (String) db.jobs.claim(Instant.now()).get("id");
        var invocations = new AtomicInteger();
        var deltas = new ArrayList<String>();
        var execution = new GenerationCallService(db.mapper(GenerationCallMapper.class), db.txManager, new AiQuotaExecution() {
            public ModelResponse complete(Long user, String operation, String id, ModelRequest request) {
                throw new AssertionError("must stream");
            }

            public void stream(
                    Long user, String operation, String id, ModelRequest request, ModelStreamHandler handler) {
                invocations.incrementAndGet();
                handler.onReasoning("private");
                handler.onContent("{\"x\":");
                handler.onContent("1}");
                handler.onUsage(17L);
            }
        });
        assertEquals("{\"x\":1}", execution.stream(db.user, job, "outline", "system", "prompt", 100, deltas::add));
        assertEquals(List.of("{\"x\":", "1}"), deltas);
        assertEquals(
                "{\"x\":1}",
                execution.stream(
                        db.user, job, "outline", "system", "prompt", 100, x -> {
                            throw new AssertionError("cache is not a live delta");
                        }));
        assertEquals(1, invocations.get());
        assertEquals(
                17L,
                db.jdbc.queryForObject(
                        "SELECT consumed_tokens FROM studyroom_fragment_versions WHERE fragment_id='f' AND revision=1",
                        Long.class));
    }
}
