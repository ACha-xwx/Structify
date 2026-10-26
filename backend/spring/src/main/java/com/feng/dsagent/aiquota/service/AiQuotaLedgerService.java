package com.feng.dsagent.aiquota.service;

import com.feng.dsagent.aiquota.domain.AiQuotaConcurrency;
import com.feng.dsagent.aiquota.domain.AiQuotaReservationStatus;
import com.feng.dsagent.aiquota.domain.AiQuotaUsageSource;
import com.feng.dsagent.aiquota.dto.AiQuotaReservationDTO;
import com.feng.dsagent.aiquota.entity.AiQuotaAccountEntity;
import com.feng.dsagent.aiquota.entity.AiQuotaBucketEntity;
import com.feng.dsagent.aiquota.entity.AiQuotaReservationEntity;
import com.feng.dsagent.aiquota.entity.AiQuotaReservationAttemptEntity;
import com.feng.dsagent.aiquota.mapper.AiQuotaLedgerMapper;
import com.feng.dsagent.aiquota.support.DevTestAccountQuotaPolicy;
import com.feng.dsagent.aiquota.error.AiQuotaErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable per-user AI token accounting. Callers reserve before a provider call and always settle or fail it.
 */
@Service
public class AiQuotaLedgerService {

    private static final Duration MAXIMUM_RESERVATION_TTL = Duration.ofHours(1);
    private static final int EXPIRY_RECOVERY_BATCH_SIZE = 500;

    private final AiQuotaLedgerMapper mapper;
    private final Clock clock;

    private DevTestAccountQuotaPolicy testAccountPolicy;

    @org.springframework.beans.factory.annotation.Autowired
    public void configureTestAccountQuota(DevTestAccountQuotaPolicy policy) {
        this.testAccountPolicy = policy;
    }

    public boolean isUnlimited(long userId) {
        return testAccountPolicy != null && testAccountPolicy.isUnlimited(userId);
    }

    public AiQuotaLedgerService(AiQuotaLedgerMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AiQuotaReservationEntity reserve(AiQuotaReservationDTO request) {
        return reserveAttempt(request).reservation();
    }

    @Transactional
    public AiQuotaReservationAttemptEntity reserveAttempt(AiQuotaReservationDTO request) {
        validate(request);
        Instant now = clock.instant();
        LocalDate quotaDate = quotaDate(now);

        ensureConcurrencyRow(request.userId(), now);
        AiQuotaConcurrency concurrency = mapper.lockConcurrency(request.userId());
        int activeReservations = recoverExpiredForUser(request.userId(), now, concurrency.activeReservations());

        ensureBucket(request.userId(), quotaDate, request.dailyTokenQuota(), now);
        AiQuotaBucketEntity bucket = lockBucket(request.userId(), quotaDate, request.dailyTokenQuota(), now);
        Optional<AiQuotaReservationEntity> existing = Optional.ofNullable(mapper.findByRequestForUpdate(
            request.userId(), quotaDate, request.requestId().trim()
        ));
        if (existing.isPresent()) {
            return new AiQuotaReservationAttemptEntity(existing.get(), false);
        }
        if (activeReservations >= request.maximumConcurrentRequests()) {
            throw AiQuotaErrorCode.CONCURRENCY_LIMITED.toException();
        }
        if (!hasCapacity(bucket, request.estimatedTokens())) {
            throw AiQuotaErrorCode.EXHAUSTED.toException();
        }

        AiQuotaReservationEntity reservation = new AiQuotaReservationEntity(
            UUID.randomUUID().toString(),
            request.userId(),
            quotaDate,
            request.requestId().trim(),
            AiQuotaReservationStatus.RESERVED,
            request.estimatedTokens(),
            null,
            now,
            now.plus(request.reservationTtl()),
            null,
            null,
            null
        );
        mapper.createReservation(reservation);
        mapper.reserveTokens(request.userId(), quotaDate, request.estimatedTokens(), now);
        mapper.incrementConcurrency(request.userId(), now);
        return new AiQuotaReservationAttemptEntity(reservation, true);
    }

    @Transactional
    public AiQuotaReservationEntity settle(String reservationId, long actualTokens) {
        return settle(reservationId, actualTokens, AiQuotaUsageSource.PROVIDER_REPORTED);
    }

    @Transactional
    public AiQuotaReservationEntity settle(String reservationId, long actualTokens, AiQuotaUsageSource usageSource) {
        requireReservationId(reservationId);
        requireActualTokens(actualTokens);
        requireUsageSource(usageSource);
        Instant now = clock.instant();
        AiQuotaReservationEntity candidate = Optional.ofNullable(mapper.findReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);
        ensureConcurrencyRow(candidate.userId(), now);
        AiQuotaConcurrency concurrency = mapper.lockConcurrency(candidate.userId());
        AiQuotaReservationEntity reservation = Optional.ofNullable(mapper.lockReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);

        if (reservation.status() == AiQuotaReservationStatus.SETTLED) {
            return reservation;
        }
        AiQuotaBucketEntity bucket = requireBucket(mapper.lockBucket(reservation.userId(), reservation.quotaDate()));
        if (reservation.status() == AiQuotaReservationStatus.EXPIRED) {
            if (mapper.markExpiredSettlement(reservationId, actualTokens, usageSource.name(), now) != 1) {
                throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException();
            }
            mapper.charge(reservation.userId(), reservation.quotaDate(), actualTokens, now);
        } else if (reservation.status() == AiQuotaReservationStatus.RESERVED) {
            requireReleasable(bucket, concurrency, reservation);
            if (mapper.markSettled(reservationId, actualTokens, usageSource.name(), now) != 1) {
                throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException();
            }
            mapper.releaseAndCharge(
                reservation.userId(), reservation.quotaDate(), reservation.estimatedTokens(), actualTokens, now
            );
            mapper.decrementConcurrency(reservation.userId(), now);
        } else {
            throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException("AI 配额预留已结束");
        }
        return Optional.ofNullable(mapper.findReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);
    }

    @Transactional
    public AiQuotaReservationEntity fail(String reservationId, long actualTokens, String failureCode) {
        AiQuotaUsageSource source = actualTokens > 0
            ? AiQuotaUsageSource.PROVIDER_REPORTED
            : AiQuotaUsageSource.NO_USAGE_REPORTED;
        return fail(reservationId, actualTokens, failureCode, source);
    }

    @Transactional
    public AiQuotaReservationEntity fail(
        String reservationId,
        long actualTokens,
        String failureCode,
        AiQuotaUsageSource usageSource
    ) {
        requireReservationId(reservationId);
        requireActualTokens(actualTokens);
        String normalizedFailureCode = normalizeFailureCode(failureCode);
        requireUsageSource(usageSource);
        Instant now = clock.instant();
        AiQuotaReservationEntity candidate = Optional.ofNullable(mapper.findReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);
        ensureConcurrencyRow(candidate.userId(), now);
        AiQuotaConcurrency concurrency = mapper.lockConcurrency(candidate.userId());
        AiQuotaReservationEntity reservation = Optional.ofNullable(mapper.lockReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);

        if (reservation.status() == AiQuotaReservationStatus.FAILED) {
            return reservation;
        }
        AiQuotaBucketEntity bucket = requireBucket(mapper.lockBucket(reservation.userId(), reservation.quotaDate()));
        if (reservation.status() == AiQuotaReservationStatus.EXPIRED) {
            if (mapper.markExpiredFailure(reservationId, actualTokens, normalizedFailureCode, usageSource.name(), now) != 1) {
                throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException();
            }
            mapper.charge(reservation.userId(), reservation.quotaDate(), actualTokens, now);
        } else if (reservation.status() == AiQuotaReservationStatus.RESERVED) {
            requireReleasable(bucket, concurrency, reservation);
            if (mapper.markFailed(reservationId, actualTokens, normalizedFailureCode, usageSource.name(), now) != 1) {
                throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException();
            }
            mapper.releaseAndCharge(
                reservation.userId(), reservation.quotaDate(), reservation.estimatedTokens(), actualTokens, now
            );
            mapper.decrementConcurrency(reservation.userId(), now);
        } else {
            throw AiQuotaErrorCode.RESERVATION_NOT_ACTIVE.toException("AI 配额预留已结束");
        }
        return Optional.ofNullable(mapper.findReservation(reservationId))
            .orElseThrow(AiQuotaErrorCode.RESERVATION_NOT_FOUND::toException);
    }

    @Transactional
    public int recoverExpiredReservations() {
        Instant now = clock.instant();
        int recovered = 0;
        List<String> candidates = mapper.expiredReservationIds(now, EXPIRY_RECOVERY_BATCH_SIZE);
        for (String reservationId : candidates) {
            if (recoverExpiredReservation(reservationId, now)) {
                recovered++;
            }
        }
        return recovered;
    }

    @Transactional(readOnly = true)
    public Optional<AiQuotaReservationEntity> findReservation(String reservationId) {
        requireReservationId(reservationId);
        return Optional.ofNullable(mapper.findReservation(reservationId));
    }

    @Transactional(readOnly = true)
    public AiQuotaAccountEntity account(long userId) {
        if (userId < 1) {
            throw AiQuotaErrorCode.USER_INVALID.toException();
        }
        LocalDate date = quotaDate(clock.instant());
        AiQuotaBucketEntity bucket = Optional.ofNullable(mapper.findBucket(userId, date))
            .orElse(new AiQuotaBucketEntity(userId, date, 0, 0, 0));
        return new AiQuotaAccountEntity(
            userId,
            date,
            bucket.dailyTokenQuota(),
            bucket.reservedTokens(),
            bucket.consumedTokens(),
            remaining(bucket),
            activeReservations(userId)
        );
    }

    private int recoverExpiredForUser(long userId, Instant now, int activeReservations) {
        int active = activeReservations;
        for (String reservationId : mapper.expiredReservationIdsForUser(userId, now)) {
            AiQuotaReservationEntity reservation = mapper.lockReservation(reservationId);
            if (reservation == null || reservation.status() != AiQuotaReservationStatus.RESERVED
                    || reservation.expiresAt().isAfter(now)) {
                continue;
            }
            AiQuotaBucketEntity bucket = requireBucket(mapper.lockBucket(reservation.userId(), reservation.quotaDate()));
            requireReleasable(bucket, new AiQuotaConcurrency(userId, active), reservation);
            if (mapper.markExpired(reservationId, now) == 1) {
                mapper.releaseAndCharge(
                    reservation.userId(), reservation.quotaDate(), reservation.estimatedTokens(), 0, now
                );
                mapper.decrementConcurrency(userId, now);
                active--;
            }
        }
        return active;
    }

    private boolean recoverExpiredReservation(String reservationId, Instant now) {
        AiQuotaReservationEntity candidate = mapper.findReservation(reservationId);
        if (candidate == null) {
            return false;
        }
        ensureConcurrencyRow(candidate.userId(), now);
        AiQuotaConcurrency concurrency = mapper.lockConcurrency(candidate.userId());
        AiQuotaReservationEntity reservation = mapper.lockReservation(reservationId);
        if (reservation == null || reservation.status() != AiQuotaReservationStatus.RESERVED
                || reservation.expiresAt().isAfter(now)) {
            return false;
        }
        AiQuotaBucketEntity bucket = requireBucket(mapper.lockBucket(reservation.userId(), reservation.quotaDate()));
        requireReleasable(bucket, concurrency, reservation);
        if (mapper.markExpired(reservationId, now) != 1) {
            return false;
        }
        mapper.releaseAndCharge(reservation.userId(), reservation.quotaDate(), reservation.estimatedTokens(), 0, now);
        mapper.decrementConcurrency(reservation.userId(), now);
        return true;
    }

    private void ensureConcurrencyRow(long userId, Instant now) {
        try {
            mapper.insertConcurrencyRow(userId, now);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void ensureBucket(long userId, LocalDate quotaDate, long dailyTokenQuota, Instant now) {
        try {
            mapper.insertBucket(userId, quotaDate, dailyTokenQuota, now);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private AiQuotaBucketEntity lockBucket(long userId, LocalDate quotaDate, long currentDailyQuota, Instant now) {
        AiQuotaBucketEntity bucket = requireBucket(mapper.lockBucket(userId, quotaDate));
        if (bucket.dailyTokenQuota() == currentDailyQuota) {
            return bucket;
        }
        mapper.updateDailyTokenQuota(userId, quotaDate, currentDailyQuota, now);
        return new AiQuotaBucketEntity(
            bucket.userId(),
            bucket.quotaDate(),
            currentDailyQuota,
            bucket.reservedTokens(),
            bucket.consumedTokens()
        );
    }

    private int activeReservations(long userId) {
        Integer active = mapper.selectActiveReservations(userId);
        return active == null ? 0 : active;
    }

    private static AiQuotaBucketEntity requireBucket(AiQuotaBucketEntity bucket) {
        if (bucket == null) {
            throw new IllegalStateException("AI quota bucket was not created");
        }
        return bucket;
    }

    private void validate(AiQuotaReservationDTO request) {
        if (request == null) {
            throw AiQuotaErrorCode.REQUEST_INVALID.toException();
        }
        if (request.userId() < 1) {
            throw AiQuotaErrorCode.USER_INVALID.toException();
        }
        if (request.dailyTokenQuota() < 1) {
            throw AiQuotaErrorCode.NOT_CONFIGURED.toException();
        }
        if (request.estimatedTokens() < 1) {
            throw AiQuotaErrorCode.ESTIMATE_INVALID.toException();
        }
        if (request.maximumConcurrentRequests() < 1) {
            throw AiQuotaErrorCode.CONCURRENCY_INVALID.toException();
        }
        if (request.requestId() == null || !request.requestId().trim().matches("^[A-Za-z0-9._:-]{1,128}$")) {
            throw AiQuotaErrorCode.REQUEST_ID_INVALID.toException();
        }
        if (request.reservationTtl() == null || request.reservationTtl().isNegative() || request.reservationTtl().isZero()
                || request.reservationTtl().compareTo(MAXIMUM_RESERVATION_TTL) > 0) {
            throw AiQuotaErrorCode.RESERVATION_TTL_INVALID.toException();
        }
    }

    private void requireReservationId(String reservationId) {
        if (reservationId == null || !reservationId.matches("^[A-Za-z0-9-]{1,64}$")) {
            throw AiQuotaErrorCode.RESERVATION_ID_INVALID.toException();
        }
    }

    private void requireActualTokens(long actualTokens) {
        if (actualTokens < 0) {
            throw AiQuotaErrorCode.USAGE_INVALID.toException();
        }
    }

    private void requireUsageSource(AiQuotaUsageSource usageSource) {
        if (usageSource == null) {
            throw AiQuotaErrorCode.USAGE_SOURCE_INVALID.toException();
        }
    }

    private String normalizeFailureCode(String failureCode) {
        if (failureCode == null || !failureCode.trim().matches("^[A-Z0-9_]{1,96}$")) {
            throw AiQuotaErrorCode.FAILURE_CODE_INVALID.toException();
        }
        return failureCode.trim();
    }

    private void requireReleasable(
        AiQuotaBucketEntity bucket,
        AiQuotaConcurrency concurrency,
        AiQuotaReservationEntity reservation
    ) {
        if (concurrency.activeReservations() < 1 || bucket.reservedTokens() < reservation.estimatedTokens()) {
            throw new IllegalStateException("AI quota ledger invariant violated");
        }
    }

    private boolean hasCapacity(AiQuotaBucketEntity bucket, long estimatedTokens) {
        if (isUnlimited(bucket.userId())) {
            return true;
        }
        if (bucket.consumedTokens() > bucket.dailyTokenQuota()) {
            return false;
        }
        long afterConsumption = bucket.dailyTokenQuota() - bucket.consumedTokens();
        if (bucket.reservedTokens() > afterConsumption) {
            return false;
        }
        return estimatedTokens <= afterConsumption - bucket.reservedTokens();
    }

    private long remaining(AiQuotaBucketEntity bucket) {
        if (isUnlimited(bucket.userId())) {
            return Long.MAX_VALUE;
        }
        if (!hasCapacity(bucket, 0)) {
            return 0;
        }
        return bucket.dailyTokenQuota() - bucket.consumedTokens() - bucket.reservedTokens();
    }

    private LocalDate quotaDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

}
