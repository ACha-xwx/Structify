package com.feng.dsagent.classroom.domain;

public record ClassroomAnswerEvaluation(
    ClassroomAnswerStatus status,
    String misconception,
    String feedback
) {

    public boolean requiresDiscussion() {
        return status != ClassroomAnswerStatus.CORRECT;
    }
}
