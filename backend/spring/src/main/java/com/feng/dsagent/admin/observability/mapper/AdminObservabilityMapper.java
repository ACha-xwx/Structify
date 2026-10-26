package com.feng.dsagent.admin.observability.mapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：运维观测所需任务与 token 用量的只读查询。
 */
@Mapper
public interface AdminObservabilityMapper {

    public Long countBackground(@Param("status") String status, @Param("userId") Long userId);

    public Long countStudyroom(@Param("status") String status, @Param("userId") Long userId);

    public List<BackgroundJobRow> selectBackground(
        @Param("status") String status,
        @Param("userId") Long userId,
        @Param("limit") int limit
    );

    public List<StudyroomJobRow> selectStudyroom(
        @Param("status") String status,
        @Param("userId") Long userId,
        @Param("limit") int limit
    );

    public List<CostRawRow> selectCostRows(@Param("from") LocalDate from, @Param("to") LocalDate to);

    public Long countStudyRooms();

    public Long countStudyroomRuns();

    public Long countStudyroomJobs();

    public Long countStudyroomQueued();

    public Long countStudyroomFailed();

    public Long countFragments();

    public Long countClassroomEvents();

    public Long countCodeRuns();

    public Long countAnimationRecords();

    public Long countLearners();

    public Long countReservationsToday();

    public Long sumTokensToday();

    public record BackgroundJobRow(
        long id,
        String taskType,
        String status,
        Timestamp createdAt,
        Timestamp finishedAt,
        String failureReason,
        Long requestedByUserId
    ) {
    }

    public record StudyroomJobRow(
        String id,
        String jobType,
        String status,
        Timestamp createdAt,
        Timestamp updatedAt,
        String failureMessage,
        Long userId,
        long tokens
    ) {
    }

    public record CostRawRow(Object quotaDate, Long userId, long calls, Long tokens) {
    }
}
