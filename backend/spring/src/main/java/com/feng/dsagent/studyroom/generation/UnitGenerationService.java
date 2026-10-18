package com.feng.dsagent.studyroom.generation;

import com.feng.dsagent.studyroom.StudyroomUnitComposer;

import org.springframework.stereotype.Service;

@Service
public class UnitGenerationService {
    private final StudyroomUnitComposer composer;

    public UnitGenerationService(StudyroomUnitComposer composer) {
        this.composer = composer;
    }

    public String generate(long user, String runId, String unitJson, String jobId) {
        return composer.compose(user, runId, unitJson, jobId);
    }
}
