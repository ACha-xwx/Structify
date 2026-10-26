package com.feng.dsagent.mail.domain;

public record MailConfigKeyBinding(long configurationId, String host, int port, String securityMode, String username) {
    public String additionalAuthenticatedData() {
        return "mail-config:" + configurationId + "\n"
            + host + "\n"
            + port + "\n"
            + securityMode + "\n"
            + username;
    }
}
