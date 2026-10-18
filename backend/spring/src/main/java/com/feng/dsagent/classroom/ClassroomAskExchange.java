package com.feng.dsagent.classroom;

/**
 * One question a learner asked while a lesson was running, and what came back.
 *
 * <p>Kept for the whole lesson rather than for the step it was asked on: a question asked three pages ago
 * is still what the next answer has to build on, and the learner reads the lesson back as one conversation
 * - the step is recorded so each exchange can still say where it happened.
 */
public record ClassroomAskExchange(int stepIndex, String question, String answer, String createdAt) {
}
