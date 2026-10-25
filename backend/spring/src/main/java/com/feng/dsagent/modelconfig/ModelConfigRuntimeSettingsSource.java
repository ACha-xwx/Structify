package com.feng.dsagent.modelconfig;

import java.util.Optional;

@FunctionalInterface
public interface ModelConfigRuntimeSettingsSource {

    public Optional<ModelConfigRuntimeSettings> current();
}
