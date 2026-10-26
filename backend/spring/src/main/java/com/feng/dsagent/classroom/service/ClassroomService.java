package com.feng.dsagent.classroom.service;

import com.feng.dsagent.classroom.converter.ClassroomScriptSummaryConverter;
import com.feng.dsagent.classroom.domain.ClassroomScript;
import com.feng.dsagent.classroom.domain.ClassroomState;
import com.feng.dsagent.classroom.domain.ClassroomStatus;
import com.feng.dsagent.classroom.entity.ClassroomSessionRecordEntity;
import com.feng.dsagent.classroom.mapper.ClassroomMapper;
import com.feng.dsagent.classroom.support.ClassroomScriptParser;
import com.feng.dsagent.classroom.support.ClassroomTimeline;
import com.feng.dsagent.classroom.vo.ClassroomScriptSummaryVO;
import com.feng.dsagent.classroom.vo.ClassroomSessionVO;
import com.feng.dsagent.classroom.domain.ClassroomAction;
import com.feng.dsagent.classroom.error.ClassroomErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private final ClassroomMapper mapper;
    private final ClassroomScriptParser parser;
    private final ClassroomTimeline timeline;
    private final ClassroomScriptSummaryConverter scriptSummaryConverter;

    public ClassroomService(
        ClassroomMapper mapper,
        ClassroomScriptParser parser,
        ClassroomTimeline timeline,
        ClassroomScriptSummaryConverter scriptSummaryConverter
    ) {
        this.mapper = mapper;
        this.parser = parser;
        this.timeline = timeline;
        this.scriptSummaryConverter = scriptSummaryConverter;
    }

    public List<ClassroomScriptSummaryVO> scripts(String chapterId) {
        List<ClassroomScript> scripts = chapterId == null || chapterId.isBlank()
            ? mapper.findPublishedScripts()
            : mapper.findPublishedScriptsByChapter(chapterId);
        return scripts.stream().map(scriptSummaryConverter::toView).toList();
    }

    @Transactional
    public ClassroomSessionVO create(long userId, String scriptId) {
        ClassroomScript script = Optional.ofNullable(mapper.findPublishedScript(scriptId))
            .orElseThrow(ClassroomErrorCode.SCRIPT_NOT_FOUND::toException);
        parser.parse(script.scriptJson());
        ClassroomStatus status = new ClassroomStatus(ClassroomState.OPENING, false);
        String id = UUID.randomUUID().toString();
        mapper.insertSession(
            id, userId, script.id(), status.state().name(), status.paused(), script.scriptJson(), script.chapterId()
        );
        ClassroomSessionRecordEntity session = new ClassroomSessionRecordEntity(
            id, userId, script.id(), script.chapterId(), status.state(), status.paused(), null, script.scriptJson()
        );
        return timeline.view(session);
    }

    public ClassroomSessionVO get(long userId, String sessionId) {
        return timeline.get(userId, sessionId);
    }

    public ClassroomSessionVO apply(long userId, String sessionId, ClassroomAction action, String content) {
        return apply(userId, sessionId, action, content, null);
    }

    public ClassroomSessionVO apply(
        long userId, String sessionId, ClassroomAction action, String content, Integer expectedRevision
    ) {
        return timeline.apply(userId, sessionId, action, content, expectedRevision);
    }

    /** Human correction of the courseware page for one step of this lesson. */
    public ClassroomSessionVO pinSlide(long userId, String sessionId, int stepIndex, String slideId) {
        return timeline.pinSlide(userId, sessionId, stepIndex, slideId);
    }
}
