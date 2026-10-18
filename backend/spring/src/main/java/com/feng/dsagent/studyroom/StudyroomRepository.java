package com.feng.dsagent.studyroom;

import java.util.List;
import java.util.Optional;

public interface StudyroomRepository {

    default String currentRun(String roomId,long userId) { return null; }

    default void appendExchange(String roomId,long userId,List<StudyroomRecords.Message> messages,List<StudyroomRecords.Step> steps,String expectedRun) { appendExchange(roomId,userId,messages,steps); }

    default boolean hasReviewedRun(String roomId,long userId) { return false; }

    boolean isPublishedChapter(String chapterId);

    void create(StudyroomRecords.Room room, List<StudyroomRecords.Step> steps, List<StudyroomRecords.Message> messages);

    Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId, long userId);

    void appendExchange(
        String studyroomId,
        long userId,
        List<StudyroomRecords.Message> messages,
        List<StudyroomRecords.Step> supplementalSteps
    );

    Optional<StudyroomRecords.Room> updateProgress(long userId, String studyroomId, String stepId);
}
