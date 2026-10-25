package com.feng.dsagent.classroom.service;

import com.feng.dsagent.classroom.ClassroomScriptSummary;
import com.feng.dsagent.classroom.ClassroomSessionRecord;
import com.feng.dsagent.classroom.ClassroomScriptParser;
import com.feng.dsagent.classroom.ClassroomSessionView;
import com.feng.dsagent.classroom.ClassroomRepository;
import com.feng.dsagent.classroom.ClassroomTimeline;
import com.feng.dsagent.classroom.ClassroomAction;
import com.feng.dsagent.classroom.ClassroomScript;
import com.feng.dsagent.classroom.ClassroomStatus;
import com.feng.dsagent.classroom.ClassroomState;

import com.feng.dsagent.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private final ClassroomRepository repository;
    private final ClassroomScriptParser parser;
    private final ClassroomTimeline timeline;
    public ClassroomService(ClassroomRepository repository, ClassroomScriptParser parser, ClassroomTimeline timeline) {
        this.repository = repository; this.parser = parser; this.timeline = timeline;
    }
    public List<ClassroomScriptSummary> scripts(String chapterId) {
        return repository.findPublishedScripts(chapterId).stream().map(ClassroomScriptSummary::from).toList();
    }
    @Transactional
    public ClassroomSessionView create(long userId, String scriptId) {
        ClassroomScript script = repository.findPublishedScript(scriptId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASSROOM_SCRIPT_NOT_FOUND", "课堂脚本不存在或尚未发布"));
        parser.parse(script.scriptJson());
        ClassroomSessionRecord session = repository.createSession(userId, script, new ClassroomStatus(ClassroomState.OPENING, false));
        return timeline.view(session);
    }
    public ClassroomSessionView get(long userId, String sessionId) { return timeline.get(userId, sessionId); }
    public ClassroomSessionView apply(long userId, String sessionId, ClassroomAction action, String content) {
        return apply(userId, sessionId, action, content, null);
    }
    public ClassroomSessionView apply(long userId, String sessionId, ClassroomAction action, String content, Integer expectedRevision) {
        return timeline.apply(userId, sessionId, action, content, expectedRevision);
    }
    /** Human correction of the courseware page for one step of this lesson. */
    public ClassroomSessionView pinSlide(long userId, String sessionId, int stepIndex, String slideId) {
        return timeline.pinSlide(userId, sessionId, stepIndex, slideId);
    }
}
