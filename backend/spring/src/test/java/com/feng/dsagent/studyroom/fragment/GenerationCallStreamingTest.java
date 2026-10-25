package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;

import com.feng.dsagent.studyroom.generation.service.GenerationCallService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.*;
import com.feng.dsagent.studyroom.generation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

class GenerationCallStreamingTest {
    @Test void streamsContentOnceAndCachesWithProviderUsage() throws Exception {
        var db=new FragmentTestDatabase();db.unit("u",0);db.fragment("f","u",0,"PENDING");
        var manager=new DataSourceTransactionManager(db.source);
        var jobs=new JdbcGenerationRepository(db.store,db.json,manager);
        new PreparationScheduler(new JdbcFragmentRepository(db.store,db.json),jobs,db.json).reconcile(db.user,"run");
        String job=(String)jobs.claim(Instant.now()).get("id");
        var invocations=new AtomicInteger(); var deltas=new ArrayList<String>();
        var execution=new GenerationCallService(StudyroomMapperFactory.create(db.source, GenerationCallMapper.class), manager, new AiQuotaExecution() {
            public ModelResponse complete(Long user,String operation,String id,ModelRequest request) {throw new AssertionError("must stream");}
            public void stream(Long user,String operation,String id,ModelRequest request,ModelStreamHandler handler) {
                invocations.incrementAndGet();handler.onReasoning("private");handler.onContent("{\"x\":");handler.onContent("1}");handler.onUsage(17L);
            }
        });
        assertEquals("{\"x\":1}",execution.stream(db.user,job,"outline","system","prompt",100,deltas::add));
        assertEquals(List.of("{\"x\":","1}"),deltas);
        assertEquals("{\"x\":1}",execution.stream(db.user,job,"outline","system","prompt",100,x->{throw new AssertionError("cache is not a live delta");}));
        assertEquals(1,invocations.get());
        assertEquals(17L,db.jdbc.queryForObject("SELECT consumed_tokens FROM studyroom_fragment_versions WHERE fragment_id='f' AND revision=1",Long.class));
    }
}
