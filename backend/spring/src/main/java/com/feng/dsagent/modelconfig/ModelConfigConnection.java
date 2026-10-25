package com.feng.dsagent.modelconfig;

public final class ModelConfigConnection {

    private final String provider;
    private final ModelConfigResolvedTarget target;
    private final String model;
    private final String apiKey;

    public ModelConfigConnection(String provider, ModelConfigResolvedTarget target, String model, String apiKey) {
        this.provider = provider;
        this.target = target;
        this.model = model;
        this.apiKey = apiKey;
    }

    public String provider() {
        return provider;
    }

    public ModelConfigResolvedTarget target() {
        return target;
    }

    public String model() {
        return model;
    }

    public String apiKey() {
        return apiKey;
    }
}
