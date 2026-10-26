package com.feng.dsagent.modelconfig.support;

import com.feng.dsagent.modelconfig.domain.ModelConfigConnection;
import com.feng.dsagent.modelconfig.domain.ModelConfigConnectionResult;
public interface ModelConfigConnectionTester {

    public ModelConfigConnectionResult test(ModelConfigConnection connection);
}
