package com.feng.dsagent.studyroom.fragment;

import static org.mockito.Mockito.mock;

import com.feng.dsagent.studyroom.fragment.mapper.FragmentPublicationMapper;

import com.feng.dsagent.studyroom.fragment.service.FragmentPublicationService;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.generation.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class FragmentPublicationServiceTest {
    @Test void publishesOneFragmentWithoutWaitingAndRejectsExpiredOrSupersededWork() {
        var db=new FragmentTestDatabase();db.unit("u",0);db.fragment("f","u",0,"PENDING");db.fragment("g","u",1,"PENDING");
        var repo=new JdbcFragmentRepository(db.store,db.json);
        var jobs=new JdbcGenerationRepository(db.store,db.json,new DataSourceTransactionManager(db.source));
        var scheduler=new PreparationScheduler(repo,jobs,db.json);scheduler.reconcile(db.user,"run");
        var job=jobs.claim(Instant.now());
        var publisher=new FragmentPublicationService(mock(FragmentPublicationMapper.class),db.json);
        var scene=new StudyroomScene("main","TEXT","讲解",List.of(),null,null,null,null);
        var output=new StudyroomModelOutput(1,"栈","栈的定义",List.of("e"),List.of(new StudyroomModelOutput.Step("f",
            StudyroomModelOutput.StepType.EXPLANATION,"栈","后进先出",List.of(),new StudyroomScene.Orchestration(2,"理解栈","CONCEPT",scene,List.of()))));
        publisher.publish(job,output.toJson(db.json.mapper()),"resources");
        publisher.publish(job,output.toJson(db.json.mapper()),"resources");
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps",Integer.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='f'",String.class)).isEqualTo("READY");
        assertThat(db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='g'",String.class)).isEqualTo("QUEUED");
        var next=jobs.claim(Instant.now());
        db.jdbc.update("UPDATE study_rooms SET current_run_id='replacement' WHERE id='room'");
        assertThatThrownBy(()->publisher.publish(next,output.toJson(db.json.mapper()),"resources"))
            .hasMessageContaining("学习路线已更新");
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps",Integer.class)).isEqualTo(1);
    }
}
