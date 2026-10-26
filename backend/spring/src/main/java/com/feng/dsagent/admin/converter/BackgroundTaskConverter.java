package com.feng.dsagent.admin.converter;

import com.feng.dsagent.admin.entity.BackgroundTaskEntity;
import com.feng.dsagent.admin.vo.BackgroundTaskVO;
import org.mapstruct.Mapper;

/**
 * 职责：将后台任务实体转换为对外 VO。
 */
@Mapper(componentModel = "spring")
public interface BackgroundTaskConverter {

    BackgroundTaskVO toView(BackgroundTaskEntity entity);
}
