package com.feng.dsagent.mail;

public interface MailTransport {
    public void testConnection(MailConnection connection, String password);

    public void send(MailConnection connection, String password, String recipient, String subject, String html);
}
