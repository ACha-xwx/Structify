package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;

import com.feng.dsagent.studyroom.domain.StudyroomScene;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentPublicationMapper;
import com.feng.dsagent.studyroom.fragment.service.FragmentPublicationService;
import com.feng.dsagent.studyroom.support.StudyroomModelOutput;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FragmentPublicationServiceTest {
    @Test
    void publishesOneFragmentWithoutWaitingAndRejectsExpiredOrSupersededWork() throws Exception {
        var db = new FragmentTestDatabase();
        db.unit("u", 0);
        db.fragment("f", "u", 0, "PENDING");
        db.fragment("g", "u", 1, "PENDING");
        db.scheduler().reconcile(db.user, "run");
        var job = db.jobs.claim(Instant.now());
        var publisher = new FragmentPublicationService(db.mapper(FragmentPublicationMapper.class), db.json);
        var scene = new StudyroomScene("main", "TEXT", "讲解", List.of(), null, null, null, null);
        var output = new StudyroomModelOutput(
                1,
                "栈",
                "栈的定义",
                List.of("e"),
                List.of(new StudyroomModelOutput.Step(
                        "f",
                        StudyroomModelOutput.StepType.EXPLANATION,
                        "栈",
                        "后进先出",
                        List.of(),
                        new StudyroomScene.Orchestration(2, "理解栈", "CONCEPT", scene, List.of()))));
        publisher.publish(job, output.toJson(db.json.mapper()), "resources");
        publisher.publish(job, output.toJson(db.json.mapper()), "resources");
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps", Integer.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='f'", String.class))
                .isEqualTo("READY");
        assertThat(db.jdbc.queryForObject("SELECT status FROM studyroom_fragments WHERE id='g'", String.class))
                .isEqualTo("QUEUED");
        var next = db.jobs.claim(Instant.now());
        db.jdbc.update("UPDATE study_rooms SET current_run_id='replacement' WHERE id='room'");
        assertThatThrownBy(() -> publisher.publish(next, output.toJson(db.json.mapper()), "resources"))
                .hasMessageContaining("学习路线已更新");
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM study_room_steps", Integer.class)).isEqualTo(1);
    }
}
