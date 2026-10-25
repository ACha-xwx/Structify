package com.feng.dsagent.resource;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository {

    public List<ChapterView> findPublishedChapters();

    public List<ResourceAsset> findPublishedByChapterId(String chapterId, ResourceAudience audience);

    public Optional<ResourceAsset> findPublishedById(String id, ResourceAudience audience);
}
