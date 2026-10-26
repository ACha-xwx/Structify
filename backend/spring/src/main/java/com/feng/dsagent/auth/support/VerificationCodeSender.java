package com.feng.dsagent.auth.support;

public interface VerificationCodeSender {
    public void send(String email, String code, String purpose);
}
