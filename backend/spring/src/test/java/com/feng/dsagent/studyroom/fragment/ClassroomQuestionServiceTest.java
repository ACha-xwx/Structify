package com.feng.dsagent.studyroom.fragment;

import org.springframework.transaction.PlatformTransactionManager;

import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;

import com.feng.dsagent.studyroom.questions.mapper.ClassroomQuestionMapper;

import com.feng.dsagent.studyroom.events.mapper.ClassroomEventMapper;

import com.feng.dsagent.studyroom.events.service.ClassroomEventService;
import com.feng.dsagent.studyroom.generation.service.GenerationCallService;
import com.feng.dsagent.studyroom.questions.service.ClassroomQuestionService;
import com.feng.dsagent.studyroom.service.StudyroomFollowUpService;
import com.feng.dsagent.studyroom.service.StudyroomService;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.events.*;
import com.feng.dsagent.studyroom.generation.*;
import com.feng.dsagent.studyroom.questions.*;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.*;
import java.util.*;
import java.time.Instant;

class ClassroomQuestionServiceTest {
    @Test void completesStreamAndPublishesExactlyOneExchange() {exerciseWorker(false);}
    @Test void cancellingDuringProviderStreamPreventsLatePublication() {exerciseWorker(true);}
    void exerciseWorker(boolean cancel) {
        var db=new FragmentTestDatabase();db.unit("u",0);db.fragment("f","u",0,"READY");
        db.jdbc.update("UPDATE studyroom_fragments SET content_revision=1 WHERE id='f'");
        var manager=new DataSourceTransactionManager(db.source);var jobs=new JdbcGenerationRepository(db.store,db.json,manager);
        var events=new ClassroomEventService(mock(ClassroomEventMapper.class), db.json, manager);
        var view=mock(StudyroomService.class);when(view.validateQuestionContext(eq(db.user),eq("room"),any())).thenReturn("{\"stepId\":\"step-f\"}");
        var repository=new JdbcClassroomQuestionRepository(db.store,db.json);
        var service=new ClassroomQuestionService(mock(ClassroomQuestionMapper.class),db.store,db.json,jobs,events,view);
        String id=(String)service.create(db.user,"room","run","f",1,"为什么？",null,"q-test",KnowledgeAudience.STUDENT).get("questionId");
        var model=new AiQuotaExecution() {
            public ModelResponse complete(Long u,String op,String key,ModelRequest request){throw new AssertionError("stream required");}
            public void stream(Long u,String op,String key,ModelRequest request,ModelStreamHandler handler) {
                handler.onContent("后进");if(cancel)service.cancel(db.user,"room",id);handler.onContent("先出。");handler.onUsage(12L);
            }
        };
        var context=mock(StudyroomFollowUpService.class);when(context.prepare(eq(db.user),eq("room"),anyString(),any(),anyString())).thenReturn(new StudyroomFollowUpService.AnswerContext("prompt","step-f","[]","run"));
        var rooms=mock(StudyroomRepository.class);
        var worker=new ClassroomQuestionWorker(repository,jobs,events,new GenerationCallService(mock(GenerationCallMapper.class), mock(PlatformTransactionManager.class), model),context,rooms);
        var job=jobs.claim(Instant.now());
        if(cancel) {
            assertThrows(RuntimeException.class,()->worker.run(job));verifyNoInteractions(rooms);
            assertEquals("CANCELLED",service.get(db.user,"room",id).get("status"));
            assertNotNull(db.jdbc.queryForObject("SELECT usage_json FROM studyroom_generation_calls",String.class));
        } else {
            worker.run(job);assertEquals("COMPLETED",service.get(db.user,"room",id).get("status"));
            assertEquals("后进先出。",service.get(db.user,"room",id).get("answer"));
            verify(rooms,times(1)).appendExchange(eq("room"),eq(db.user),argThat(messages->messages.size()==2&&messages.get(1).content().equals("后进先出。")),eq("run"));
            assertTrue(events.read(db.user,"question:"+id,0).snapshot().terminal());
        }
        assertEquals("READY",db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='f'",String.class));
    }
    @Test void createsOnePrioritizedUnboundJobAndCancelsWithoutTouchingSource() {
        var db=new FragmentTestDatabase();db.unit("u",0);db.fragment("f","u",0,"READY");
        db.jdbc.update("UPDATE studyroom_fragments SET content_revision=1 WHERE id='f'");
        var manager=new DataSourceTransactionManager(db.source);
        var jobs=new JdbcGenerationRepository(db.store,db.json,manager);
        var events=new ClassroomEventService(mock(ClassroomEventMapper.class), db.json, manager);
        var rooms=mock(StudyroomService.class);
        when(rooms.validateQuestionContext(eq(db.user),eq("room"),any())).thenReturn("{\"stepId\":\"step-f\"}");
        var service=new ClassroomQuestionService(mock(ClassroomQuestionMapper.class),db.store,db.json,jobs,events,rooms);
        var first=service.create(db.user,"room","run","f",1,"为什么？",null,"question-1",KnowledgeAudience.STUDENT);
        assertEquals(first,service.create(db.user,"room","run","f",1,"为什么？",null,"question-1",KnowledgeAudience.STUDENT));
        assertThrows(RuntimeException.class,()->service.create(db.user,"room","run","f",1,"另一个问题",null,"question-2",KnowledgeAudience.STUDENT));
        var job=db.jdbc.queryForMap("SELECT * FROM studyroom_generation_jobs WHERE job_type='FOLLOWUP'");
        assertNull(job.get("unit_id"));assertNull(job.get("fragment_id"));assertEquals(400,((Number)job.get("priority")).intValue());
        String id=(String)first.get("questionId");
        assertThrows(RuntimeException.class,()->service.get(db.user+1,"room",id));
        service.cancel(db.user,"room",id);
        assertEquals("CANCELLED",service.get(db.user,"room",id).get("status"));
        assertEquals("READY",db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='f'",String.class));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_generation_calls",Integer.class));
    }
}
