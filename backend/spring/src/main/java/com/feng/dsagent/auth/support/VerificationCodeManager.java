package com.feng.dsagent.auth.support;

import com.feng.dsagent.auth.domain.VerificationCodeDelivery;
public interface VerificationCodeManager {
    public VerificationCodeDelivery request(String email, String purpose);
    public void consume(String email, String purpose, String code);
}
