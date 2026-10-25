package com.feng.dsagent.compiler;

public interface CompilerGateway {

    public CompilerExecution execute(SupportedLanguage language, String code, String stdin);
}
