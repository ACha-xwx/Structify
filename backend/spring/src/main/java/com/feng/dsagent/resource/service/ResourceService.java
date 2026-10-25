package com.feng.dsagent.resource.service;

import com.feng.dsagent.resource.ResourceRepository;
import com.feng.dsagent.resource.ResourceAudience;
import com.feng.dsagent.resource.ResourceAsset;
import com.feng.dsagent.resource.ResourceView;
import com.feng.dsagent.resource.ChapterView;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {
    private final ResourceRepository resources;

    public ResourceService(ResourceRepository resources) {
        this.resources = resources;
    }

    public List<ChapterView> chapters() {
        return resources.findPublishedChapters();
    }

    public List<ResourceView> resources(String chapterId, AuthenticatedUser user) {
        return resources.findPublishedByChapterId(chapterId, ResourceAudience.from(user)).stream()
            .map(ResourceAsset::view)
            .toList();
    }

    public ResourceView resource(String id, AuthenticatedUser user) {
        return resources.findPublishedById(id, ResourceAudience.from(user)).map(ResourceAsset::view)
            .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "资料不存在"
        ));
    }
}
