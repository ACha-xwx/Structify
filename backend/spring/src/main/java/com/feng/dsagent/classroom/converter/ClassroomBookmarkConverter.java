package com.feng.dsagent.classroom.converter;

import com.feng.dsagent.classroom.entity.ClassroomBookmarkEntity;
import com.feng.dsagent.classroom.vo.ClassroomBookmarkVO;
import org.mapstruct.Mapper;

/**
 * 职责：将课堂收藏实体转换为对外 VO。
 */
@Mapper(componentModel = "spring")
public interface ClassroomBookmarkConverter {

    ClassroomBookmarkVO toView(ClassroomBookmarkEntity entity);
}
