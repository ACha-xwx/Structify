package com.feng.dsagent.modelconfig.support;

import java.util.Optional;

import com.feng.dsagent.modelconfig.domain.ModelConfigRuntimeSettings;
@FunctionalInterface
public interface ModelConfigRuntimeSettingsSource {

    public Optional<ModelConfigRuntimeSettings> current();
}
