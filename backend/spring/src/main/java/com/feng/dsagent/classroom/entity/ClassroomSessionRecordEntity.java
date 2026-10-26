package com.feng.dsagent.classroom.entity;

import com.feng.dsagent.classroom.domain.ClassroomState;
public record ClassroomSessionRecordEntity(
    String id,
    long userId,
    String scriptId,
    String chapterId,
    ClassroomState state,
    boolean paused,
    String summary,
    String scriptJson
) {
}
