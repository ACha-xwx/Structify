package com.feng.dsagent.resource.converter;

import com.feng.dsagent.resource.mapper.ResourceMapper;
import com.feng.dsagent.resource.support.ResourceAsset;
import com.feng.dsagent.resource.vo.ChapterVO;
import com.feng.dsagent.resource.vo.ResourceVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 职责：将资料与章节查询行转换为对外 VO（含派生 contentUrl）。
 */
@Mapper(componentModel = "spring")
public interface ResourceConverter {

    @Mapping(target = "type", source = "resourceType")
    @Mapping(target = "contentUrl", source = ".", qualifiedByName = "contentUrl")
    ResourceVO toView(ResourceMapper.ResourceRow row);

    ChapterVO toView(ResourceMapper.ChapterRow row);

    default ResourceAsset toAsset(ResourceMapper.ResourceRow row) {
        return new ResourceAsset(toView(row), row.filePath());
    }

    @Named("contentUrl")
    default String contentUrl(ResourceMapper.ResourceRow row) {
        String filePath = row.filePath();
        return filePath == null || filePath.isBlank() ? null : "/api/v1/resources/" + row.id() + "/content";
    }
}
