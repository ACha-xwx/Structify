package com.feng.dsagent.classroom;

import java.util.List;
import java.util.Optional;

interface ClassroomRepository {

    List<ClassroomScript> findPublishedScripts(String chapterId);

    Optional<ClassroomScript> findPublishedScript(String id);

    ClassroomSessionRecord createSession(long userId, ClassroomScript script, ClassroomStatus status);

    Optional<ClassroomSessionRecord> findSession(String id, long userId);

    ClassroomSessionRecord updateSession(ClassroomSessionRecord session, ClassroomStatus status, String summary);

    /** One question the learner asked at one step, kept so the next answer knows what was already said. */
    void recordAsk(String sessionId, int stepIndex, String question, String answer);

    /** The questions asked at one step, oldest first. */
    List<ClassroomAskExchange> asksAt(String sessionId, int stepIndex);

    void appendEvent(ClassroomEventRecord event);

}
