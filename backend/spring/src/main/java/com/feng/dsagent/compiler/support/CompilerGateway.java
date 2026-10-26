package com.feng.dsagent.compiler.support;

import com.feng.dsagent.compiler.domain.CompilerExecution;
import com.feng.dsagent.compiler.domain.SupportedLanguage;
public interface CompilerGateway {

    public CompilerExecution execute(SupportedLanguage language, String code, String stdin);
}
