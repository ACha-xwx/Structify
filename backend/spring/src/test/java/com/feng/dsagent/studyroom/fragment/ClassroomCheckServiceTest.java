package com.feng.dsagent.studyroom.fragment;

import static org.junit.jupiter.api.Assertions.*;

import com.feng.dsagent.studyroom.checks.mapper.ClassroomCheckMapper;
import com.feng.dsagent.studyroom.checks.service.ClassroomCheckService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassroomCheckServiceTest {
    static final String QUIZ =
            "{\"checks\":[{\"prompt\":\"入栈1、2后首先弹出谁？\",\"options\":[{\"id\":\"a\",\"text\":\"1\"},{\"id\":\"b\",\"text\":\"2\"}],\"answerOption\":\"b\",\"analysis\":\"栈遵循后进先出。\"}]}";

    @Test
    void hidesAnswersGradesOnServerAndRetainsIndependentAttempts() throws Exception {
        var db = new FragmentTestDatabase();
        db.unit("u", 0);
        db.fragment("f", "u", 0, "PENDING");
        db.scheduler().reconcile(db.user, "run");
        var job = db.jobs.claim(Instant.now());
        db.jdbc.update(
                "UPDATE studyroom_fragments SET content_revision=1,step_id='step-f',status='READY' WHERE id='f'");
        var checks = new ClassroomCheckService(
                db.store, db.mapper(ClassroomCheckMapper.class), db.json, db.jobs);
        var items = new ArrayList<Object>();
        for (int i = 0; i < 3; i++) {
            var q = new LinkedHashMap<>(
                    (Map<String, Object>) ((List<?>) db.json.object(QUIZ).get("checks")).getFirst());
            q.put("prompt", "题目" + i);
            items.add(q);
        }
        checks.publish(job, db.json.write(Map.of("checks", items)));
        var state = checks.get(db.user, "run", "u");
        String serialized = db.json.write(state);
        assertFalse(serialized.contains("answerOption"));
        assertFalse(serialized.contains("analysis"));
        var question = (Map<?, ?>) ((List<?>) state.get("questions")).getFirst();
        String id = (String) question.get("id");
        var first = checks.attempt(db.user, "run", id, "a", "attempt-a");
        assertEquals(false, first.get("correct"));
        assertEquals("b", first.get("answerOption"));
        assertEquals(first, checks.attempt(db.user, "run", id, "a", "attempt-a"));
        assertEquals(true, checks.attempt(db.user, "run", id, "b", "attempt-b").get("correct"));
        assertEquals(2, db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_check_attempts", Integer.class));
        assertEquals(
                0,
                db.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM studyroom_learning_progress WHERE completed=TRUE", Integer.class));
        assertThrows(RuntimeException.class, () -> checks.get(db.user + 1, "run", "u"));
        assertThrows(RuntimeException.class, () -> checks.attempt(db.user, "run", id, "x", "invalid"));
        db.jdbc.update("UPDATE studyroom_fragments SET content_revision=2 WHERE id='f'");
        assertThrows(RuntimeException.class, () -> checks.attempt(db.user, "run", id, "b", "stale"));
    }

    @Test
    void rejectsAmbiguousOptionsAndStripsOnlyOptionalChecks() throws Exception {
        var db = new FragmentTestDatabase();
        var service = new ClassroomCheckService(
                db.store, db.mapper(ClassroomCheckMapper.class), db.json, db.jobs);
        assertThrows(
                RuntimeException.class,
                () -> service.validate(
                        db.json.object(QUIZ.replace("\"b\",\"text\":\"2\"", "\"a\",\"text\":\"2\"")).get("checks")));
        assertEquals("{\"title\":\"讲解\"}", service.core("{\"title\":\"讲解\",\"checks\":[]} "));
    }
}
