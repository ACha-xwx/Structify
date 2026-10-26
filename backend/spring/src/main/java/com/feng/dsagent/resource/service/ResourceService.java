package com.feng.dsagent.resource.service;

import com.feng.dsagent.resource.converter.ResourceConverter;
import com.feng.dsagent.resource.mapper.ResourceMapper;
import com.feng.dsagent.resource.domain.ResourceAudience;
import com.feng.dsagent.resource.support.ResourceAsset;
import com.feng.dsagent.resource.vo.ResourceVO;
import com.feng.dsagent.resource.vo.ChapterVO;

import com.feng.dsagent.resource.error.ResourceErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {
    private final ResourceMapper resources;
    private final ResourceConverter resourceConverter;

    public ResourceService(ResourceMapper resources, ResourceConverter resourceConverter) {
        this.resources = resources;
        this.resourceConverter = resourceConverter;
    }

    public List<ChapterVO> chapters() {
        return resources.selectPublishedChapters().stream()
            .map(resourceConverter::toView)
            .toList();
    }

    public List<ResourceVO> resources(String chapterId, AuthenticatedUser user) {
        ResourceAudience audience = ResourceAudience.from(user);
        return resources.selectPublishedByChapterId(
                chapterId,
                audience.allowClassroomOnly(),
                audience.allowTeamOnly()
            ).stream()
            .map(resourceConverter::toAsset)
            .map(ResourceAsset::view)
            .toList();
    }

    public ResourceVO resource(String id, AuthenticatedUser user) {
        ResourceAudience audience = ResourceAudience.from(user);
        ResourceMapper.ResourceRow row = resources.selectPublishedById(
            id,
            audience.allowClassroomOnly(),
            audience.allowTeamOnly()
        );
        if (row == null) {
            throw ResourceErrorCode.NOT_FOUND.toException();
        }
        return resourceConverter.toAsset(row).view();
    }
}
