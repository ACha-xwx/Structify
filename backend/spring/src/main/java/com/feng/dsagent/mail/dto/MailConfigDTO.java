package com.feng.dsagent.mail.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MailConfigDTO(
    @NotBlank @Size(max = 128) String siteName,
    boolean enabled,
    @Size(max = 253) String smtpHost,
    @NotNull @Min(1) @Max(65535) Integer smtpPort,
    @NotBlank @Size(max = 16) String securityMode,
    @Size(max = 320) String smtpUsername,
    @Size(max = 4096) String smtpPassword,
    boolean clearSmtpPassword,
    @Size(max = 254) String fromEmail,
    @NotBlank @Size(max = 128) String fromName,
    @NotNull @Min(1) @Max(30) Integer connectionTimeoutSeconds,
    @NotNull @Min(1) @Max(60) Integer verificationTtlMinutes,
    @NotNull @Min(5) @Max(3600) Integer resendIntervalSeconds,
    @NotNull @Min(1) @Max(90) Integer sessionTtlDays,
    @NotBlank @Size(max = 300) String verificationSubject,
    @NotBlank @Size(max = 100000) String verificationTemplateHtml
) {}
