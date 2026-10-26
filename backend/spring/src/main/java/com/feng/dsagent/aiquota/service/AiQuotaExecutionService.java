package com.feng.dsagent.aiquota.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.aiquota.config.AiQuotaExecutionProperties;
import com.feng.dsagent.aiquota.entity.AiQuotaReservationAttemptEntity;
import com.feng.dsagent.aiquota.dto.AiQuotaReservationDTO;
import com.feng.dsagent.aiquota.domain.AiQuotaReservationStatus;
import com.feng.dsagent.aiquota.exception.AiStreamAbortedException;
import com.feng.dsagent.aiquota.entity.AiQuotaReservationEntity;
import com.feng.dsagent.aiquota.domain.AiQuotaUsageSource;
import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.aiquota.support.AiQuotaRequestId;
import com.feng.dsagent.aiquota.entity.AiQuotaAccountEntity;
import com.feng.dsagent.aiquota.service.AiQuotaLedgerService;
import com.feng.dsagent.aiquota.error.AiQuotaErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.exception.ModelClientException;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.model.ModelStreamHandler;
import com.feng.dsagent.modelconfig.support.ModelGenerationReadiness;
import java.util.Objects;

@Service
public class AiQuotaExecutionService implements AiQuotaExecution {

    private static final long DEFAULT_ESTIMATED_OUTPUT_TOKENS = 1_024L;

    private final ModelClient model;
    private final AiQuotaLedgerService ledger;
    private final AiQuotaExecutionProperties properties;
    private final ModelGenerationReadiness modelReadiness;

    public AiQuotaExecutionService(
        ModelClient model,
        AiQuotaLedgerService ledger,
        AiQuotaExecutionProperties properties,
        ModelGenerationReadiness modelReadiness
    ) {
        this.model = Objects.requireNonNull(model, "model");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.modelReadiness = Objects.requireNonNull(modelReadiness, "modelReadiness");
    }

    @Override
    public ModelResponse complete(Long userId, String operation, String requestId, ModelRequest request) {
        requireFormalAuthentication(userId);
        Objects.requireNonNull(request, "request");
        ModelGenerationReadiness.State state = modelReadiness.current();
        long dailyTokenQuota = configuredQuota(state);
        AiQuotaReservationAttemptEntity attempt = ledger.reserveAttempt(new AiQuotaReservationDTO(
            userId,
            dailyTokenQuota,
            estimatedTokens(request),
            properties.maximumConcurrentRequests(),
            AiQuotaRequestId.operationScoped(operation, requestId),
            properties.reservationTtl()
        ));
        if (!attempt.newlyReserved()) {
            throw existingRequest(attempt.reservation());
        }

        ModelResponse response;
        try {
            response = model.complete(request);
        } catch (ModelClientException error) {
            settleFailure(
                attempt.reservation(),
                error.code(),
                failedUsage(attempt.reservation(), error.consumedTokens(), null, false),
                error
            );
            throw error;
        } catch (RuntimeException error) {
            settleFailure(attempt.reservation(), "AI_MODEL_EXECUTION_FAILED", noUsage(), error);
            throw error;
        }

        Usage usage = successUsage(response, attempt.reservation());
        ledger.settle(attempt.reservation().id(), usage.tokens(), usage.source());
        return response;
    }

    @Override
    public void stream(
        Long userId,
        String operation,
        String requestId,
        ModelRequest request,
        ModelStreamHandler handler
    ) {
        requireFormalAuthentication(userId);
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        ModelGenerationReadiness.State state = modelReadiness.current();
        long dailyTokenQuota = configuredQuota(state);
        AiQuotaReservationAttemptEntity attempt = ledger.reserveAttempt(new AiQuotaReservationDTO(
            userId,
            dailyTokenQuota,
            estimatedTokens(request),
            properties.maximumConcurrentRequests(),
            AiQuotaRequestId.operationScoped(operation, requestId),
            properties.reservationTtl()
        ));
        if (!attempt.newlyReserved()) {
            throw existingRequest(attempt.reservation());
        }

        StreamProgress progress = new StreamProgress();
        try {
            model.stream(request, new ModelStreamHandler() {
                @Override
                public void onContent(String content) {
                    if (content != null && !content.isEmpty()) {
                        progress.receivedContent = true;
                    }
                    handler.onContent(content);
                }

                @Override
                public void onReasoning(String content) {
                    if (content != null && !content.isEmpty()) {
                        progress.receivedContent = true;
                    }
                    handler.onReasoning(content);
                }

                @Override
                public void onUsage(Long totalTokens) {
                    if (totalTokens != null && totalTokens >= 0) {
                        progress.reportedTokens = totalTokens;
                    }
                    handler.onUsage(totalTokens);
                }
            });
            Usage usage = progress.reportedTokens == null
                ? new Usage(attempt.reservation().estimatedTokens(), AiQuotaUsageSource.RESERVATION_ESTIMATE)
                : new Usage(progress.reportedTokens, AiQuotaUsageSource.PROVIDER_REPORTED);
            ledger.settle(attempt.reservation().id(), usage.tokens(), usage.source());
        } catch (ModelClientException error) {
            settleFailure(
                attempt.reservation(),
                error.code(),
                failedUsage(
                    attempt.reservation(),
                    error.consumedTokens(),
                    progress.reportedTokens,
                    progress.receivedContent
                ),
                error
            );
            throw error;
        } catch (RuntimeException error) {
            settleFailure(
                attempt.reservation(),
                error instanceof AiStreamAbortedException
                    ? "AI_STREAM_CLIENT_DISCONNECTED"
                    : "AI_STREAM_EXECUTION_FAILED",
                failedUsage(attempt.reservation(), null, progress.reportedTokens, progress.receivedContent),
                error
            );
            throw error;
        }
    }

    @Override
    public void requireFormalAuthentication(Long userId) {
        if (userId == null) {
            throw AiQuotaErrorCode.AUTHENTICATION_REQUIRED.toException();
        }
    }

    public Availability availability(Long userId) {
        ModelGenerationReadiness.State state = modelReadiness.current();
        if (userId == null || !state.eligible() || state.dailyTokenQuota() == null || state.dailyTokenQuota() < 1) {
            return Availability.notConfigured();
        }
        ledger.recoverExpiredReservations();
        AiQuotaAccountEntity account = ledger.account(userId);
        if (ledger.isUnlimited(userId)) {
            boolean available = account.activeReservations() < properties.maximumConcurrentRequests();
            return new Availability(null, null, account.activeReservations(),
                available ? "UNLIMITED" : "CONCURRENCY_LIMITED", available);
        }
        long dailyTokenQuota = state.dailyTokenQuota();
        long remaining = Math.max(0L, dailyTokenQuota - account.reservedTokens() - account.consumedTokens());
        String status = remaining < 1
            ? "EXHAUSTED"
            : account.activeReservations() >= properties.maximumConcurrentRequests()
                ? "CONCURRENCY_LIMITED"
                : "AVAILABLE";
        return new Availability(dailyTokenQuota, remaining, account.activeReservations(), status, "AVAILABLE".equals(status));
    }

    private long configuredQuota(ModelGenerationReadiness.State state) {
        if (!state.eligible() || state.dailyTokenQuota() == null || state.dailyTokenQuota() < 1) {
            throw AiQuotaErrorCode.NOT_CONFIGURED_UNAVAILABLE.toException();
        }
        return state.dailyTokenQuota();
    }

    private long estimatedTokens(ModelRequest request) {
        long output = request.maxTokens() == null || request.maxTokens() < 1
            ? DEFAULT_ESTIMATED_OUTPUT_TOKENS
            : request.maxTokens().longValue();
        long input = request.messages().stream()
            .mapToLong(message -> Math.max(1L, (message.content().length() + 3L) / 4L))
            .sum();
        try {
            return Math.addExact(output, input);
        } catch (ArithmeticException error) {
            throw AiQuotaErrorCode.ESTIMATE_TOO_LARGE.toException();
        }
    }

    private Usage successUsage(ModelResponse response, AiQuotaReservationEntity reservation) {
        Long reportedTokens = response.totalTokens();
        if (reportedTokens != null && reportedTokens >= 0) {
            return new Usage(reportedTokens, AiQuotaUsageSource.PROVIDER_REPORTED);
        }
        return new Usage(reservation.estimatedTokens(), AiQuotaUsageSource.RESERVATION_ESTIMATE);
    }

    private void settleFailure(
        AiQuotaReservationEntity reservation,
        String failureCode,
        Usage usage,
        RuntimeException original
    ) {
        try {
            ledger.fail(reservation.id(), usage.tokens(), failureCode, usage.source());
        } catch (RuntimeException settlementFailure) {
            original.addSuppressed(settlementFailure);
        }
    }

    private Usage failedUsage(
        AiQuotaReservationEntity reservation,
        Long errorReportedTokens,
        Long streamedReportedTokens,
        boolean receivedContent
    ) {
        if (streamedReportedTokens != null && streamedReportedTokens >= 0) {
            return new Usage(streamedReportedTokens, AiQuotaUsageSource.PROVIDER_REPORTED);
        }
        if (errorReportedTokens != null && errorReportedTokens >= 0) {
            return new Usage(errorReportedTokens, AiQuotaUsageSource.PROVIDER_REPORTED);
        }
        if (receivedContent) {
            return new Usage(reservation.estimatedTokens(), AiQuotaUsageSource.RESERVATION_ESTIMATE);
        }
        return noUsage();
    }

    private Usage noUsage() {
        return new Usage(0L, AiQuotaUsageSource.NO_USAGE_REPORTED);
    }

    private BizException existingRequest(AiQuotaReservationEntity reservation) {
        return reservation.status() == AiQuotaReservationStatus.RESERVED
            ? AiQuotaErrorCode.REQUEST_IN_PROGRESS.toException()
            : AiQuotaErrorCode.REQUEST_ALREADY_FINALIZED.toException();
    }

    private record Usage(long tokens, AiQuotaUsageSource source) {
    }

    private static final class StreamProgress {
        private boolean receivedContent;
        private Long reportedTokens;
    }

    public record Availability(
        Long dailyTokenQuota,
        Long remainingTokens,
        int activeReservations,
        String status,
        boolean allowsFormalGeneration
    ) {

        private static Availability notConfigured() {
            return new Availability(null, null, 0, "NOT_CONFIGURED", false);
        }
    }
}
