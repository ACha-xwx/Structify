package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.studyroom.draft.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomEventRepositoryTest {
    final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:events-" + UUID.randomUUID()
        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    final JdbcTemplate db = new JdbcTemplate(source);
    final OutlineJson json = new OutlineJson(JsonMapper.builder().build());
    final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    final ClassroomEventService service;
    final long user;

    ClassroomEventRepositoryTest() {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        db.update("INSERT INTO users(email,password_hash) VALUES('events@example.com','hash')");
        user = db.queryForObject("SELECT id FROM users WHERE email='events@example.com'", Long.class);
        db.update("INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES('draft',?,'q','STANDARD','GENERATING')", user);
        service = new ClassroomEventService(new JdbcClassroomEventRepository(new JdbcOutlineRepository(db), json), new DataSourceTransactionManager(source));
        service.open(user, "outline:draft", "OUTLINE", "draft", null, "operation", 1, null, 1, Map.of("phase", "QUEUED"));
    }

    @Test void appendAndSnapshotShareCursor() {
        service.append(user,"outline:draft","operation",1,"phase",Map.of("phase","PLANNING"),Map.of("phase","PLANNING"),false);
        service.append(user,"outline:draft","operation",1,"outline_item",Map.of("title","栈"),Map.of("title","栈"),false);
        var batch = service.read(user,"outline:draft",0);
        assertEquals(List.of(1L,2L), batch.events().stream().map(ClassroomEvent::sequence).toList());
        assertEquals(2, batch.snapshot().cursor());
        assertEquals("栈", batch.snapshot().state().get("title"));
    }

    @Test void rollbackDoesNotLeaveEventOrCursor() {
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.append(user,"outline:draft","operation",1,"phase",Map.of(),Map.of("bad",true),false);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0,service.read(user,"outline:draft",0).snapshot().cursor());
        assertTrue(service.read(user,"outline:draft",0).events().isEmpty());
    }

    @Test void refusesOtherUserDeletedEntityOldAttemptAndTerminalAppend() {
        assertThrows(RuntimeException.class, () -> service.read(user+1,"outline:draft",0));
        assertThrows(RuntimeException.class, () -> service.append(user,"outline:draft","operation",0,"phase",Map.of(),Map.of(),false));
        service.append(user,"outline:draft","operation",1,"failed",Map.of(),Map.of("failed",true),true);
        assertThrows(RuntimeException.class, () -> service.append(user,"outline:draft","operation",1,"outline_item",Map.of(),Map.of(),false));
        db.update("UPDATE study_outline_drafts SET deleted_at=CURRENT_TIMESTAMP WHERE id='draft'");
        assertThrows(RuntimeException.class, () -> service.read(user,"outline:draft",0));
    }

    @Test void retentionPreservesSnapshotAndRequestsReset() {
        service.append(user,"outline:draft","operation",1,"failed",Map.of(),Map.of("failed",true),true);
        db.update("UPDATE studyroom_event_streams SET terminal_at=DATEADD('DAY',-2,CURRENT_TIMESTAMP)");
        service.cleanup();
        var batch=service.read(user,"outline:draft",0);
        assertTrue(batch.reset());
        assertEquals(1,batch.snapshot().cursor());
        assertEquals(true,batch.snapshot().state().get("failed"));
        assertTrue(batch.events().isEmpty());
    }

    @Test void concurrentWritersAllocateUniqueOrderedSequences() throws Exception {
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var writes=new ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<12;i++) writes.add(executor.submit(() -> service.append(user,"outline:draft","operation",1,"phase",Map.of(),Map.of(),false)));
            for(var write:writes) write.get();
        }
        var batch=service.read(user,"outline:draft",0);
        assertEquals(12,batch.events().size());
        for(int i=0;i<12;i++) assertEquals(i+1,batch.events().get(i).sequence());
    }

    @Test void newAttemptRequiresSnapshotAndPreservesStateVersion() {
        service.append(user,"outline:draft","operation",1,"phase",Map.of(),Map.of(),false);
        service.open(user,"outline:draft","OUTLINE","draft",null,"operation",2,null,1,Map.of("phase","QUEUED"));
        service.append(user,"outline:draft","operation",2,"phase",Map.of(),Map.of(),false);
        assertTrue(service.read(user,"outline:draft",0).reset());
        var event=service.read(user,"outline:draft",1).events().getFirst();
        assertEquals(2,event.sequence());
        assertEquals(3,event.stateVersion());
        assertThrows(RuntimeException.class, () -> service.append(user,"outline:draft","operation",1,"phase",Map.of(),Map.of(),false));
    }
}
