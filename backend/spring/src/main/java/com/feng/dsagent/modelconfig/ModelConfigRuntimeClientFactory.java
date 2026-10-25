package com.feng.dsagent.modelconfig;

import com.feng.dsagent.model.ModelClient;

@FunctionalInterface
public interface ModelConfigRuntimeClientFactory {

    public ModelClient create(ModelConfigRuntimeSettings settings);
}
