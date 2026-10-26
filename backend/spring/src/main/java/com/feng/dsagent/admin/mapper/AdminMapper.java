package com.feng.dsagent.admin.mapper;

import com.feng.dsagent.admin.domain.UserStatus;
import com.feng.dsagent.admin.entity.AdminAuditEventEntity;
import com.feng.dsagent.admin.entity.AdminUserEntity;
import com.feng.dsagent.admin.query.AdminAuditQuery;
import com.feng.dsagent.admin.query.AdminUserQuery;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：管理端用户与审计事件的持久化访问。
 */
@Mapper
public interface AdminMapper {

    long countUsers(AdminUserQuery query);

    List<AdminUserRow> selectUserRows(AdminUserQuery query);

    AdminUserRow selectUserRow(@Param("userId") long userId);

    AdminUserRow lockUserRow(@Param("userId") long userId);

    List<String> selectRoles(@Param("userId") long userId);

    List<Long> lockActiveAdministrators();

    long activeAdministratorCount();

    int updateStatusActive(@Param("userId") long userId);

    int updateStatusDisabled(@Param("userId") long userId, @Param("reason") String reason);

    int deleteRoles(@Param("userId") long userId);

    int insertRole(@Param("userId") long userId, @Param("role") String role);

    int touchUserUpdatedAt(@Param("userId") long userId);

    int appendAuditEvent(
        @Param("actorUserId") long actorUserId,
        @Param("action") String action,
        @Param("targetType") String targetType,
        @Param("targetId") String targetId,
        @Param("result") String result,
        @Param("requestId") String requestId,
        @Param("beforeSummary") String beforeSummary,
        @Param("afterSummary") String afterSummary
    );

    long countAuditEvents(AdminAuditQuery query);

    List<AdminAuditEventEntity> selectAuditEvents(AdminAuditQuery query);

    default List<AdminUserEntity> listUsers(AdminUserQuery query) {
        return selectUserRows(query).stream().map(this::toEntity).toList();
    }

    default Optional<AdminUserEntity> findUser(long userId) {
        return Optional.ofNullable(selectUserRow(userId)).map(this::toEntity);
    }

    default Optional<AdminUserEntity> lockUser(long userId) {
        return Optional.ofNullable(lockUserRow(userId)).map(this::toEntity);
    }

    default void updateStatus(long userId, UserStatus status, String reason) {
        if (status == UserStatus.ACTIVE) {
            updateStatusActive(userId);
            return;
        }
        updateStatusDisabled(userId, reason);
    }

    default void replaceRoles(long userId, Set<String> roles) {
        deleteRoles(userId);
        for (String role : roles.stream().sorted().toList()) {
            insertRole(userId, role);
        }
        touchUserUpdatedAt(userId);
    }

    default AdminUserEntity toEntity(AdminUserRow row) {
        return new AdminUserEntity(
            row.id(),
            row.email(),
            row.username(),
            row.status(),
            row.disabledReason(),
            row.disabledAt(),
            new LinkedHashSet<>(selectRoles(row.id())),
            row.createdAt(),
            row.updatedAt()
        );
    }

    record AdminUserRow(
        long id,
        String email,
        String username,
        UserStatus status,
        String disabledReason,
        Instant disabledAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
