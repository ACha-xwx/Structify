package com.feng.dsagent.auth.mapper;

import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：邮箱验证码哈希与尝试次数的持久化访问。
 */
@Mapper
public interface VerificationCodeMapper {

    public int insertCode(
        @Param("email") String email,
        @Param("purpose") String purpose,
        @Param("codeHash") String codeHash,
        @Param("expiresAt") Timestamp expiresAt
    );

    public Timestamp selectLatestCreatedAt(@Param("email") String email, @Param("purpose") String purpose);

    public VerificationCodeRow selectLatestActive(@Param("email") String email, @Param("purpose") String purpose);

    public int incrementAttemptsIfActive(
        @Param("id") long id,
        @Param("now") Timestamp now,
        @Param("maximumAttempts") int maximumAttempts
    );

    public int consumeIfActive(
        @Param("id") long id,
        @Param("now") Timestamp now,
        @Param("maximumAttempts") int maximumAttempts
    );

    public record VerificationCodeRow(long id, String codeHash, int attempts, Timestamp expiresAt) {
    }
}
