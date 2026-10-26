package com.feng.dsagent.aiquota.mapper;

import com.feng.dsagent.aiquota.domain.AiQuotaConcurrency;
import com.feng.dsagent.aiquota.entity.AiQuotaBucketEntity;
import com.feng.dsagent.aiquota.entity.AiQuotaReservationEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiQuotaLedgerMapper {

    int insertConcurrencyRow(@Param("userId") long userId, @Param("now") Instant now);

    AiQuotaConcurrency lockConcurrency(@Param("userId") long userId);

    Integer selectActiveReservations(@Param("userId") long userId);

    int insertBucket(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("dailyTokenQuota") long dailyTokenQuota,
        @Param("now") Instant now
    );

    AiQuotaBucketEntity lockBucket(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate);

    int updateDailyTokenQuota(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("dailyTokenQuota") long dailyTokenQuota,
        @Param("now") Instant now
    );

    AiQuotaBucketEntity findBucket(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate);

    AiQuotaReservationEntity findReservation(@Param("reservationId") String reservationId);

    AiQuotaReservationEntity lockReservation(@Param("reservationId") String reservationId);

    AiQuotaReservationEntity findByRequestForUpdate(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("requestId") String requestId
    );

    List<String> expiredReservationIdsForUser(@Param("userId") long userId, @Param("now") Instant now);

    List<String> expiredReservationIds(@Param("now") Instant now, @Param("maximumCount") int maximumCount);

    int createReservation(AiQuotaReservationEntity reservation);

    int reserveTokens(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("tokens") long tokens,
        @Param("now") Instant now
    );

    int releaseAndCharge(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("reservedTokens") long reservedTokens,
        @Param("actualTokens") long actualTokens,
        @Param("now") Instant now
    );

    int charge(
        @Param("userId") long userId,
        @Param("quotaDate") LocalDate quotaDate,
        @Param("actualTokens") long actualTokens,
        @Param("now") Instant now
    );

    int incrementConcurrency(@Param("userId") long userId, @Param("now") Instant now);

    int decrementConcurrency(@Param("userId") long userId, @Param("now") Instant now);

    int markSettled(
        @Param("reservationId") String reservationId,
        @Param("actualTokens") long actualTokens,
        @Param("usageSource") String usageSource,
        @Param("now") Instant now
    );

    int markExpiredSettlement(
        @Param("reservationId") String reservationId,
        @Param("actualTokens") long actualTokens,
        @Param("usageSource") String usageSource,
        @Param("now") Instant now
    );

    int markFailed(
        @Param("reservationId") String reservationId,
        @Param("actualTokens") long actualTokens,
        @Param("failureCode") String failureCode,
        @Param("usageSource") String usageSource,
        @Param("now") Instant now
    );

    int markExpiredFailure(
        @Param("reservationId") String reservationId,
        @Param("actualTokens") long actualTokens,
        @Param("failureCode") String failureCode,
        @Param("usageSource") String usageSource,
        @Param("now") Instant now
    );

    int markExpired(@Param("reservationId") String reservationId, @Param("now") Instant now);
}
