package com.feng.dsagent.mail.vo;

import java.time.Instant;

public record MailConfigVO(
    String siteName,
    boolean enabled,
    String smtpHost,
    int smtpPort,
    String securityMode,
    String smtpUsername,
    boolean smtpPasswordConfigured,
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
) {}
