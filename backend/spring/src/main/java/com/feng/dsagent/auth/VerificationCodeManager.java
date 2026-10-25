package com.feng.dsagent.auth;

public interface VerificationCodeManager {
    public VerificationCodeDelivery request(String email, String purpose);
    public void consume(String email, String purpose, String code);
}
