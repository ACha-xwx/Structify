package com.feng.dsagent.modelconfig.support;

import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.config.ModelProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.feng.dsagent.modelconfig.domain.ModelConfigRuntimeSettings;
@Component
public class OpenAiCompatibleModelConfigRuntimeClientFactory implements ModelConfigRuntimeClientFactory {

    private final ModelProperties defaults;
    private final ObjectMapper objectMapper;
    private final PinnedHttpsTransport transport;

    public OpenAiCompatibleModelConfigRuntimeClientFactory(
        ModelProperties defaults,
        ObjectMapper objectMapper,
        PinnedHttpsTransport transport
    ) {
        this.defaults = defaults;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public ModelClient create(ModelConfigRuntimeSettings settings) {
        return new PinnedOpenAiCompatibleModelClient(settings, defaults, objectMapper, transport);
    }
}
