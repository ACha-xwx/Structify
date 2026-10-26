package com.feng.dsagent.admin.mapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：按用户汇总学习活动与用量的只读查询。
 */
@Mapper
public interface AdminUserLearningMapper {

    public List<RecentJobRow> selectRecentJobs(@Param("userId") long userId, @Param("limit") int limit);

    public UserRowData selectUser(@Param("userId") long userId);

    public Long countBookmarks(@Param("userId") long userId);

    public Long countClassroomSessions(@Param("userId") long userId);

    public Long countClassroomEvents(@Param("userId") long userId);

    public Timestamp selectLastActivity(@Param("userId") long userId);

    public Long sumTokensSince(@Param("userId") long userId, @Param("since") LocalDate since);

    public Long countCallsSince(@Param("userId") long userId, @Param("since") LocalDate since);

    public record RecentJobRow(String id, String jobType, String status, Timestamp createdAt, long tokens) {
    }

    public record UserRowData(long id, String email, String username, String status) {
    }
}
