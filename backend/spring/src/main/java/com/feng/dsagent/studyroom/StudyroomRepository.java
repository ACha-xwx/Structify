package com.feng.dsagent.studyroom;
import java.util.List;
import java.util.Optional;
public interface StudyroomRepository {
    String currentRun(String roomId,long userId);
    Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId,long userId);
    void appendExchange(String roomId,long userId,List<StudyroomRecords.Message> messages,String expectedRun);
    Optional<StudyroomRecords.Room> updateProgress(long userId,String studyroomId,String stepId);
}
