package com.feng.dsagent.classroom;

/**
 * One question a learner asked in the middle of a step, and what came back.
 *
 * <p>Kept per step so the next answer knows what has already been said, and so the questions asked during
 * a lesson can be read back at all - the session itself only ever holds its latest response.
 */
public record ClassroomAskExchange(String question, String answer, String createdAt) {
}
