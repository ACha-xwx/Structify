package com.feng.dsagent.presentation;

import java.util.List;

/** One rendered slide page, as published by the offline deck pipeline. */
public record PresentationSlide(
    String id,
    String deckId,
    String deckTitle,
    int slideNumber,
    String chapter,
    String title,
    String rawText,
    String speakerNotes,
    String semanticSummary,
    String teachingRole,
    String teachingFocus,
    List<String> concepts,
    List<String> visualAnchors,
    boolean shouldShow,
    List<String> lessonIds,
    String imageUrl,
    String section,
    String role,
    List<String> terms
) {
}
