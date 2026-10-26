package com.feng.dsagent.classroom.converter;

import com.feng.dsagent.classroom.domain.ClassroomScript;
import com.feng.dsagent.classroom.vo.ClassroomScriptSummaryVO;
import org.mapstruct.Mapper;

/**
 * 职责：将课堂脚本领域对象转换为对外脚本摘要 VO。
 */
@Mapper(componentModel = "spring")
public interface ClassroomScriptSummaryConverter {

    ClassroomScriptSummaryVO toView(ClassroomScript script);
}
