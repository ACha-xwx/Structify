package com.feng.dsagent.animation;

public interface AnimationRepository {

    public boolean isPublishedChapter(String chapterId);

    public String save(long userId, String chapterId, AnimationDefinition definition, String payloadJson);
}
