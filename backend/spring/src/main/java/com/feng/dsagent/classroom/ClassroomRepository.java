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

    /** Everything asked during one lesson, oldest first, across all of its steps. */
    List<ClassroomAskExchange> asksOf(String sessionId);

    void appendEvent(ClassroomEventRecord event);

    /** One step of a class: how many answers it has taken so far, and whether a hint came before. */
    record StepAttempts(int answers, int hints) {
    }

    /** Counts the answers and hints already recorded at one step, so the next event can say which try it is. */
    StepAttempts attemptsAt(String sessionId, int stepIndex);

}
