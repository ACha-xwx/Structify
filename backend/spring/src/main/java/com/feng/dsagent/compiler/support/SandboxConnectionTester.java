package com.feng.dsagent.compiler.support;

import com.feng.dsagent.compiler.domain.SandboxConnectionResult;
import com.feng.dsagent.compiler.domain.SandboxRuntimeSettings;
public interface SandboxConnectionTester {

    public SandboxConnectionResult test(SandboxRuntimeSettings settings);
}
