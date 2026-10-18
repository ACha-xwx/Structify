package com.feng.dsagent.studyroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudyroomFollowUpContext(@NotBlank @Size(max=64) String stepId, @NotBlank @Size(max=160) String sceneId,
    @Size(max=160) String resourceId, @Size(max=16) String codeMode, @Valid RunResult runResult) {
    public record RunResult(@Size(max=4000) String stdout, @Size(max=4000) String stderr) {}
}
