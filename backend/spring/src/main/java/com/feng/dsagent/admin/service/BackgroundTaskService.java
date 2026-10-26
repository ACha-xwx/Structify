package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.converter.BackgroundTaskConverter;
import com.feng.dsagent.admin.entity.BackgroundTaskEntity;
import com.feng.dsagent.admin.error.AdminErrorCode;
import com.feng.dsagent.admin.mapper.AdminMapper;
import com.feng.dsagent.admin.mapper.BackgroundTaskMapper;
import com.feng.dsagent.admin.query.BackgroundTaskQuery;
import com.feng.dsagent.admin.vo.BackgroundTaskVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.common.support.PageParams;
import com.feng.dsagent.common.support.StringNormalizer;
import com.feng.dsagent.common.vo.PageVO;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职责：管理端后台任务的查询、重试与过期回收。
 */
@Service
public class BackgroundTaskService {
    private static final Set<String> STATUSES = Set.of(
        "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED"
    );
    private static final String STALE_TASK_RECOVERY = "STALE_TASK_RECOVERY";
    private static final Duration TASK_EXECUTION_DEADLINE = Duration.ofMinutes(1);

    private final BackgroundTaskMapper mapper;
    private final AdminMapper adminMapper;
    private final BackgroundTaskConverter backgroundTaskConverter;
    private final Clock clock;

    public BackgroundTaskService(
        BackgroundTaskMapper mapper,
        AdminMapper adminMapper,
        BackgroundTaskConverter backgroundTaskConverter,
        Clock clock
    ) {
        this.mapper = mapper;
        this.adminMapper = adminMapper;
        this.backgroundTaskConverter = backgroundTaskConverter;
        this.clock = clock;
    }

    public PageVO<BackgroundTaskVO> list(int page, int size, String requestedStatus, String requestedTaskType) {
        PageParams.requireValid(page, size, AdminErrorCode.PAGE_INVALID);
        BackgroundTaskQuery query = new BackgroundTaskQuery(
            page,
            size,
            status(requestedStatus),
            taskType(requestedTaskType)
        );
        long total = mapper.countTasks(query);
        return PageVO.of(
            page,
            size,
            total,
            mapper.selectTasks(query).stream().map(backgroundTaskConverter::toView).toList()
        );
    }

    public BackgroundTaskVO task(long taskId) {
        return backgroundTaskConverter.toView(require(taskId));
    }

    @Transactional
    public BackgroundTaskVO submitTimeoutRecovery(AuthenticatedUser actor, String requestId) {
        Instant now = clock.instant();
        BackgroundTaskEntity task = mapper.createRecoveryTask(
            actor.userId(), safeRequestId(requestId), now, now.plus(TASK_EXECUTION_DEADLINE)
        );
        appendAudit(actor, "BACKGROUND_TASK_SUBMITTED", task, task, "SUCCESS", requestId);
        return backgroundTaskConverter.toView(task);
    }

    @Transactional
    public BackgroundTaskVO retry(AuthenticatedUser actor, long taskId, String requestId) {
        BackgroundTaskEntity before = mapper.lockOptional(taskId).orElseThrow(() -> notFound(taskId));
        if (!"FAILED".equals(before.status()) || !STALE_TASK_RECOVERY.equals(before.taskType())
                || before.retryCount() >= before.maxAttempts()) {
            throw AdminErrorCode.BACKGROUND_TASK_RETRY_NOT_ALLOWED.toException();
        }
        mapper.queueRetry(taskId, clock.instant().plus(TASK_EXECUTION_DEADLINE));
        BackgroundTaskEntity after = require(taskId);
        appendAudit(actor, "BACKGROUND_TASK_RETRIED", before, after, "SUCCESS", requestId);
        return backgroundTaskConverter.toView(after);
    }

    @Transactional
    public BackgroundTaskVO cancel(AuthenticatedUser actor, long taskId, String requestId) {
        BackgroundTaskEntity before = mapper.lockOptional(taskId).orElseThrow(() -> notFound(taskId));
        if (!"PENDING".equals(before.status()) || !STALE_TASK_RECOVERY.equals(before.taskType())) {
            throw AdminErrorCode.BACKGROUND_TASK_CANCEL_NOT_ALLOWED.toException();
        }
        mapper.cancel(taskId, clock.instant());
        BackgroundTaskEntity after = require(taskId);
        appendAudit(actor, "BACKGROUND_TASK_CANCELED", before, after, "SUCCESS", requestId);
        return backgroundTaskConverter.toView(after);
    }

    public void dispatch(long taskId) {
        Thread.ofVirtual().name("background-task-" + taskId + "-").start(() -> execute(taskId));
    }

    @Scheduled(fixedDelayString = "${app.background-tasks.timeout-recovery-interval:PT30S}")
    public void recoverExpiredTasks() {
        mapper.failExpired(clock.instant());
    }

    private void execute(long taskId) {
        Instant startedAt = clock.instant();
        if (mapper.start(taskId, startedAt, startedAt.plus(TASK_EXECUTION_DEADLINE)) != 1) {
            return;
        }
        try {
            int recoveredTaskCount = mapper.failExpired(clock.instant());
            mapper.complete(taskId, clock.instant(), recoveredTaskCount);
        } catch (RuntimeException ignored) {
            mapper.fail(
                taskId,
                clock.instant(),
                "BACKGROUND_TASK_EXECUTION_FAILED",
                "Task execution did not complete"
            );
        }
    }

    private String status(String value) {
        String normalized = StringNormalizer.upper(value);
        if (normalized == null) {
            return null;
        }
        if (!STATUSES.contains(normalized)) {
            throw AdminErrorCode.BACKGROUND_TASK_STATUS_INVALID.toException();
        }
        return normalized;
    }

    private String taskType(String value) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^[A-Za-z0-9_.-]{1,64}$")) {
            throw AdminErrorCode.BACKGROUND_TASK_TYPE_INVALID.toException();
        }
        return normalized;
    }

    private BackgroundTaskEntity require(long taskId) {
        if (taskId < 1) {
            throw notFound(taskId);
        }
        return mapper.findOptional(taskId).orElseThrow(() -> notFound(taskId));
    }

    private BizException notFound(long taskId) {
        return AdminErrorCode.BACKGROUND_TASK_NOT_FOUND.toException();
    }

    private void appendAudit(
        AuthenticatedUser actor,
        String action,
        BackgroundTaskEntity before,
        BackgroundTaskEntity after,
        String result,
        String requestId
    ) {
        adminMapper.appendAuditEvent(
            actor.userId(),
            action,
            "BACKGROUND_TASK",
            Long.toString(after.id()),
            result,
            safeRequestId(requestId),
            summary(before),
            summary(after)
        );
    }

    private static String summary(BackgroundTaskEntity task) {
        return "status=" + task.status() + ";type=" + task.taskType() + ";retryCount=" + task.retryCount()
            + (task.resultCount() == null ? "" : ";resultCount=" + task.resultCount());
    }

    private static String safeRequestId(String value) {
        String normalized = StringNormalizer.optional(value);
        return normalized == null ? "" : normalized.substring(0, Math.min(normalized.length(), 128));
    }
}
