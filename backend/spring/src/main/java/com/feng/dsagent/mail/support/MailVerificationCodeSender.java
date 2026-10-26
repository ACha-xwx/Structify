package com.feng.dsagent.mail.support;

import com.feng.dsagent.mail.service.MailConfigService;

import com.feng.dsagent.auth.support.VerificationCodeSender;
import org.springframework.stereotype.Component;

@Component
public final class MailVerificationCodeSender implements VerificationCodeSender {

    private final MailConfigService mail;

    public MailVerificationCodeSender(MailConfigService mail) {
        this.mail = mail;
    }

    @Override
    public void send(String email, String code, String purpose) {
        mail.sendVerification(email, code, purpose);
    }
}
