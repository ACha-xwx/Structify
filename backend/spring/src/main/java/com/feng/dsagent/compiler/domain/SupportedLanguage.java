package com.feng.dsagent.compiler.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SupportedLanguage {
    C("c", "gcc", "main.c"),
    PYTHON("python", "python", "main.py");

    private final String apiName;
    private final String pistonRuntime;
    private final String fileName;

    private SupportedLanguage(String apiName, String pistonRuntime, String fileName) {
        this.apiName = apiName;
        this.pistonRuntime = pistonRuntime;
        this.fileName = fileName;
    }

    public String apiName() {
        return apiName;
    }

    public String pistonRuntime() {
        return pistonRuntime;
    }

    public String fileName() {
        return fileName;
    }

    public static Optional<SupportedLanguage> fromApiName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(language -> language.apiName.equals(normalized))
            .findFirst();
    }
}
