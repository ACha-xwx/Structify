package com.feng.dsagent.studyroom;
import java.util.List;
import java.util.Optional;
public interface StudyroomRepository {
    public String currentRun(String roomId,long userId);
    public Optional<StudyroomRecords.Aggregate> findOwned(String studyroomId,long userId);
    public void appendExchange(String roomId,long userId,List<StudyroomRecords.Message> messages,String expectedRun);
    public Optional<StudyroomRecords.Room> updateProgress(long userId,String studyroomId,String stepId);
}
