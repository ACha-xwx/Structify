package com.feng.dsagent.model;

public interface ModelClient {

    public ModelResponse complete(ModelRequest request);

    public void stream(ModelRequest request, ModelStreamHandler handler);
}
