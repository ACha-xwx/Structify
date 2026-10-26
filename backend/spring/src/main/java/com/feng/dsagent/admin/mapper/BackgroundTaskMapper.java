package com.feng.dsagent.admin.mapper;

import com.feng.dsagent.admin.entity.BackgroundTaskEntity;
import com.feng.dsagent.admin.query.BackgroundTaskQuery;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：后台任务行的持久化与状态流转更新。
 */
@Mapper
public interface BackgroundTaskMapper {

    long countTasks(BackgroundTaskQuery query);

    java.util.List<BackgroundTaskEntity> selectTasks(BackgroundTaskQuery query);

    BackgroundTaskEntity find(@Param("taskId") long taskId);

    BackgroundTaskEntity lock(@Param("taskId") long taskId);

    int insertRecoveryTask(RecoveryTaskInsert row);

    int start(
        @Param("taskId") long taskId,
        @Param("startedAt") Instant startedAt,
        @Param("deadlineAt") Instant deadlineAt
    );

    int complete(
        @Param("taskId") long taskId,
        @Param("completedAt") Instant completedAt,
        @Param("resultCount") int resultCount
    );

    int fail(
        @Param("taskId") long taskId,
        @Param("failedAt") Instant failedAt,
        @Param("code") String code,
        @Param("reason") String reason
    );

    int queueRetry(@Param("taskId") long taskId, @Param("deadlineAt") Instant deadlineAt);

    int cancel(@Param("taskId") long taskId, @Param("canceledAt") Instant canceledAt);

    int failExpired(@Param("now") Instant now);

    default Optional<BackgroundTaskEntity> findOptional(long taskId) {
        return Optional.ofNullable(find(taskId));
    }

    default Optional<BackgroundTaskEntity> lockOptional(long taskId) {
        return Optional.ofNullable(lock(taskId));
    }

    default BackgroundTaskEntity createRecoveryTask(
        long actorUserId,
        String requestId,
        Instant createdAt,
        Instant deadlineAt
    ) {
        RecoveryTaskInsert row = new RecoveryTaskInsert();
        row.setRequestedByUserId(actorUserId);
        row.setRequestId(requestId);
        row.setCreatedAt(createdAt);
        row.setDeadlineAt(deadlineAt);
        insertRecoveryTask(row);
        return findOptional(row.getId()).orElseThrow();
    }

    class RecoveryTaskInsert {
        private Long id;
        private long requestedByUserId;
        private String requestId;
        private Instant createdAt;
        private Instant deadlineAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getRequestedByUserId() {
            return requestedByUserId;
        }

        public void setRequestedByUserId(long requestedByUserId) {
            this.requestedByUserId = requestedByUserId;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getDeadlineAt() {
            return deadlineAt;
        }

        public void setDeadlineAt(Instant deadlineAt) {
            this.deadlineAt = deadlineAt;
        }
    }
}
