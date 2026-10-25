package com.feng.dsagent.mail;

public record MailConnection(
    String siteName,
    String host,
    int port,
    SecurityMode securityMode,
    String username,
    String fromEmail,
    String fromName,
    int connectionTimeoutSeconds
) {
}
