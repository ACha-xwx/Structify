package com.feng.dsagent.auth.support;

import com.feng.dsagent.auth.config.AuthProperties;
import com.feng.dsagent.auth.error.AuthErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.common.WindowRateLimiter;
import com.feng.dsagent.mail.service.MailConfigService;
import com.feng.dsagent.mail.domain.MailPolicy;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.feng.dsagent.auth.domain.VerificationCodeDelivery;
import com.feng.dsagent.auth.entity.VerificationCodeRecordEntity;
import com.feng.dsagent.auth.mapper.VerificationCodeMapper;
@Service
public class DefaultVerificationCodeManager implements VerificationCodeManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeMapper codes;
    private final VerificationCodeSender sender;
    private final AuthProperties properties;
    private final VerificationCodeHasher hasher;
    private final Clock clock;
    private final MailConfigService mailConfig;
    private final WindowRateLimiter requestLimiter = new WindowRateLimiter(3, Duration.ofMinutes(10));
    private final TransactionTemplate requiresNew;

    public DefaultVerificationCodeManager(
        VerificationCodeMapper codes,
        VerificationCodeSender sender,
        AuthProperties properties,
        com.feng.dsagent.security.config.SecurityProperties securityProperties,
        MailConfigService mailConfig,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.codes = codes;
        this.sender = sender;
        this.properties = properties;
        this.hasher = new VerificationCodeHasher(securityProperties.jwtSecret());
        this.mailConfig = mailConfig;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public VerificationCodeDelivery request(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        Instant now = clock.instant();
        if (!requestLimiter.allow(normalizedEmail, now)) {
            throw AuthErrorCode.AUTH_CODE_RATE_LIMITED.toException();
        }
        MailPolicy policy = mailConfig.policy();
        if (Optional.ofNullable(codes.selectLatestCreatedAt(normalizedEmail, normalizedPurpose))
            .map(Timestamp::toInstant)
            .map(createdAt -> now.isBefore(createdAt.plus(policy.resendInterval())))
            .orElse(false)) {
            throw AuthErrorCode.AUTH_CODE_RESEND_TOO_SOON.toException();
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.insertCode(
            normalizedEmail,
            normalizedPurpose,
            hasher.hash(normalizedEmail, normalizedPurpose, code),
            Timestamp.from(now.plus(policy.verificationTtl()))
        );
        sender.send(normalizedEmail, code, normalizedPurpose);
        String developmentCode = properties.exposeDevelopmentCode() && !mailConfig.deliveryEnabled() ? code : null;
        return new VerificationCodeDelivery("如果邮箱状态符合要求，验证码将发送到该邮箱", developmentCode);
    }

    @Override
    public void consume(String email, String purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        VerificationCodeMapper.VerificationCodeRow row = codes.selectLatestActive(normalizedEmail, normalizedPurpose);
        if (row == null) {
            throw invalidCode();
        }
        VerificationCodeRecordEntity record = new VerificationCodeRecordEntity(
            row.id(),
            row.codeHash(),
            row.attempts(),
            row.expiresAt().toInstant()
        );
        Instant now = clock.instant();
        if (!now.isBefore(record.expiresAt()) || record.attempts() >= properties.maximumCodeAttempts()) {
            throw invalidCode();
        }
        String actualHash = hasher.hash(normalizedEmail, normalizedPurpose, code == null ? "" : code.trim());
        if (!hasher.matches(record.codeHash(), actualHash)) {
            requiresNew.executeWithoutResult(status ->
                codes.incrementAttemptsIfActive(
                    record.id(),
                    Timestamp.from(now),
                    properties.maximumCodeAttempts()
                )
            );
            throw invalidCode();
        }
        if (codes.consumeIfActive(
            record.id(),
            Timestamp.from(now),
            properties.maximumCodeAttempts()
        ) != 1) {
            throw invalidCode();
        }
    }

    private BizException invalidCode() {
        return AuthErrorCode.AUTH_CODE_INVALID.toException();
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw AuthErrorCode.AUTH_EMAIL_INVALID.toException();
        }
        return normalized;
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toLowerCase(Locale.ROOT);
        if (!SetHolder.PURPOSES.contains(normalized)) {
            throw AuthErrorCode.AUTH_PURPOSE_INVALID.toException();
        }
        return normalized;
    }

    private static final class SetHolder {
        private static final java.util.Set<String> PURPOSES = java.util.Set.of("register", "reset");
    }
}
