package com.feng.dsagent.compiler.dto;

public record RunCodeDTO(String language, String code, String stdin, String chapterId) {

    public RunCodeDTO(String language, String code, String stdin) {
        this(language, code, stdin, null);
    }
}
