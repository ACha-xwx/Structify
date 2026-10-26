package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.mapper.AdminMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single door through which any module records an auditable action.
 *
 * <p>The audit table and its mapper stay inside the admin module; other packages (the teacher
 * console, for one) are expected to write through this service so that every privileged read or change shows up
 * on the same audit page with the same shape.
 */
@Service
public class AdminAuditService {

    private final AdminMapper mapper;

    public AdminAuditService(AdminMapper mapper) {
        this.mapper = mapper;
    }

    /** Records one action. {@code beforeSummary}/{@code afterSummary} may be null when nothing changed state. */
    public void record(
        long actorUserId,
        String action,
        String targetType,
        String targetId,
        String result,
        String requestId,
        String beforeSummary,
        String afterSummary
    ) {
        // The ledger columns are NOT NULL; an absent summary is stored as an empty string, never as null.
        mapper.appendAuditEvent(
            actorUserId,
            action,
            targetType,
            targetId,
            result,
            requestId == null ? "" : requestId,
            beforeSummary == null ? "" : beforeSummary,
            afterSummary == null ? "" : afterSummary
        );
    }

    /**
     * Same as {@link #record} but commits in a fresh transaction so the row survives an outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCommitted(
        long actorUserId,
        String action,
        String targetType,
        String targetId,
        String result,
        String requestId,
        String beforeSummary,
        String afterSummary
    ) {
        record(actorUserId, action, targetType, targetId, result, requestId, beforeSummary, afterSummary);
    }

    /** Convenience for a successful privileged read, where there is no before/after state to keep. */
    public void recordRead(long actorUserId, String action, String targetType, String targetId, String requestId) {
        record(actorUserId, action, targetType, targetId, "SUCCESS", requestId, null, null);
    }
}
