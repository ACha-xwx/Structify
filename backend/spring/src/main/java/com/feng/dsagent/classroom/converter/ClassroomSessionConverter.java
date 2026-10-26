package com.feng.dsagent.classroom.converter;

import com.feng.dsagent.classroom.domain.ClassroomAnswerEvaluation;
import com.feng.dsagent.classroom.entity.ClassroomSessionRecordEntity;
import com.feng.dsagent.classroom.vo.ClassroomSessionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tools.jackson.databind.JsonNode;

/**
 * 职责：将会话实体的纯字段与调用方提供的 stage / evaluation 组装为对外会话 VO。
 */
@Mapper(componentModel = "spring")
public interface ClassroomSessionConverter {

    @Mapping(target = "stage", source = "stage")
    @Mapping(target = "answerEvaluation", source = "answerEvaluation")
    ClassroomSessionVO toView(
        ClassroomSessionRecordEntity session,
        JsonNode stage,
        ClassroomAnswerEvaluation answerEvaluation
    );
}
