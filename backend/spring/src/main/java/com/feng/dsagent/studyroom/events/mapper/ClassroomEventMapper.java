package com.feng.dsagent.studyroom.events.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomEventMapper {
    public Long lockUser(@Param("userId") long userId);
    public Map<String, Object> selectStream(@Param("streamId") String streamId, @Param("lock") boolean lock);
    public Map<String, Object> selectOwnedStream(@Param("streamId") String streamId, @Param("userId") long userId, @Param("lock") boolean lock);
    public Map<String, Object> selectOutlineAccessible(@Param("entityId") Object entityId, @Param("userId") long userId);
    public Map<String, Object> selectRunAccessible(@Param("runId") Object runId, @Param("userId") long userId);
    public Map<String, Object> selectOwnedDraft(@Param("userId") long userId, @Param("draftId") String draftId);
    public Map<String, Object> selectCurrentRun(@Param("runId") String runId, @Param("userId") long userId);
    public Map<String, Object> selectFirstReadableFragment(@Param("runId") String runId);
    public Map<String, Object> selectActiveLease(@Param("jobId") Object jobId, @Param("userId") long userId,
        @Param("attempt") Object attempt, @Param("leaseToken") Object leaseToken);
    public int updateStreamAttempt(@Param("operationId") String operationId, @Param("attempt") int attempt,
        @Param("runId") String runId, @Param("revision") Integer revision, @Param("snapshotJson") String snapshotJson,
        @Param("streamId") String streamId);
    public int insertStream(@Param("streamId") String streamId, @Param("userId") long userId, @Param("roomId") String roomId,
        @Param("entityKind") String entityKind, @Param("entityId") String entityId, @Param("operationId") String operationId,
        @Param("attempt") int attempt, @Param("runId") String runId, @Param("revision") Integer revision,
        @Param("snapshotJson") String snapshotJson);
    public int insertEvent(@Param("streamId") String streamId, @Param("sequence") long sequence, @Param("kind") String kind,
        @Param("operationId") String operationId, @Param("attempt") int attempt, @Param("stateVersion") long stateVersion,
        @Param("runId") Object runId, @Param("revision") Object revision, @Param("payloadJson") String payloadJson);
    public int updateStreamAfterAppend(@Param("sequence") long sequence, @Param("stateVersion") long stateVersion,
        @Param("snapshotJson") String snapshotJson, @Param("terminal") boolean terminal,
        @Param("terminalAt") Timestamp terminalAt, @Param("streamId") String streamId);
    public Map<String, Object> selectJob(@Param("jobId") Object jobId, @Param("userId") long userId);
    public int markStreamTerminal(@Param("streamId") String streamId);
    public List<Map<String, Object>> selectEvents(@Param("streamId") String streamId, @Param("since") long since, @Param("cursor") long cursor);
    public List<Map<String, Object>> selectExpiredTerminalStreams(@Param("before") Timestamp before);
    public Map<String, Object> selectTerminalStreamForCleanup(@Param("streamId") Object streamId, @Param("before") Timestamp before);
    public int deleteEvents(@Param("streamId") Object streamId);
    public int raiseFloor(@Param("streamId") Object streamId);
}
