package com.feng.dsagent.animation.vo;

import com.feng.dsagent.animation.domain.AnimationDefinition;
public record AnimationGenerationVO(
    AnimationDefinition definition,
    String recordId,
    boolean persisted
) {
}
