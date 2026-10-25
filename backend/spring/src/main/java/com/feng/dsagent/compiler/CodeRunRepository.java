package com.feng.dsagent.compiler;

import java.util.Optional;

public interface CodeRunRepository {

    public String save(long userId, String chapterId, RunCodeRequest request, RunCodeResponse response);

    default boolean chapterExists(String chapterId) {
        return true;
    }

    default Optional<CodeRunSnapshot> findOwned(String runId, long userId) {
        return Optional.empty();
    }
}
