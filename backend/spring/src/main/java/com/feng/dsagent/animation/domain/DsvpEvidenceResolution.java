package com.feng.dsagent.animation.domain;

public record DsvpEvidenceResolution(
    String chapterId,
    String sourceRef,
    String matchSource
) {

    public static DsvpEvidenceResolution preview() {
        return new DsvpEvidenceResolution(null, null, "NONE");
    }

    public boolean persistable() {
        return chapterId != null && !chapterId.isBlank();
    }
}
