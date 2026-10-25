package com.feng.dsagent.auth;

public interface VerificationCodeSender {
    public void send(String email, String code, String purpose);
}
