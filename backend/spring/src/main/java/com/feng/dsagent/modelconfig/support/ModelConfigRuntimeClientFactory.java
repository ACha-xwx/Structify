package com.feng.dsagent.modelconfig.support;

import com.feng.dsagent.model.ModelClient;

import com.feng.dsagent.modelconfig.domain.ModelConfigRuntimeSettings;
@FunctionalInterface
public interface ModelConfigRuntimeClientFactory {

    public ModelClient create(ModelConfigRuntimeSettings settings);
}
