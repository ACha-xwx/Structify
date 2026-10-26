package com.feng.dsagent.classroom.vo;

import tools.jackson.databind.JsonNode;

import com.feng.dsagent.classroom.domain.ClassroomAnswerEvaluation;
import com.feng.dsagent.classroom.domain.ClassroomState;
public record ClassroomSessionVO(
    String id,
    long userId,
    String scriptId,
    ClassroomState state,
    boolean paused,
    String summary,
    JsonNode stage,
    ClassroomAnswerEvaluation answerEvaluation
) {
}
