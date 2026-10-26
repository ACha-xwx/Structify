package com.feng.dsagent.mail.entity;

import java.time.Instant;

public record MailConfigEntity(
    long id,
    String siteName,
    boolean enabled,
    String smtpHost,
    int smtpPort,
    String securityMode,
    String smtpUsername,
    String smtpPasswordCiphertext,
    String fromEmail,
    String fromName,
    int connectionTimeoutSeconds,
    int verificationTtlMinutes,
    int resendIntervalSeconds,
    int sessionTtlDays,
    String verificationSubject,
    String verificationTemplateHtml,
    String lastConnectionTestStatus,
    Instant lastConnectionTestedAt,
    Instant updatedAt
) {
}
