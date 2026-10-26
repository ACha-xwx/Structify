package com.feng.dsagent.classroom.domain;

public record ClassroomScript(
    String id,
    String chapterId,
    String title,
    String versionLabel,
    String scriptJson
) {
}
