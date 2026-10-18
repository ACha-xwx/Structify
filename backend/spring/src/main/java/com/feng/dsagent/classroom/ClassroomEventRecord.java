package com.feng.dsagent.classroom;

/**
 * One thing that happened in a class, kept as evidence.
 *
 * <p>The learning detail - which step, which question, which attempt, whether a hint came first - is what
 * makes the record usable as evidence of learning rather than just an audit line: an answer given on the
 * third try is not the same as an answer given straight away.
 */
public record ClassroomEventRecord(
    String sessionId,
    ClassroomAction action,
    String content,
    ClassroomState fromState,
    ClassroomState toState,
    ClassroomAnswerEvaluation answerEvaluation,
    int stepIndex,
    String questionId,
    int attemptNo,
    boolean hintUsed
) {
}
