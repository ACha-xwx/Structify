package com.feng.dsagent.modelconfig.mapper;

import com.feng.dsagent.modelconfig.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：模型配置单行的持久化访问。
 */
@Mapper
public interface ModelConfigMapper {

    ModelConfigEntity selectById(@Param("id") long id);

    int updateKeepingConnectionTest(
        @Param("id") long id,
        @Param("provider") String provider,
        @Param("baseUrl") String baseUrl,
        @Param("model") String model,
        @Param("apiKeyCiphertext") String apiKeyCiphertext,
        @Param("temperature") double temperature,
        @Param("maxOutputTokens") int maxOutputTokens,
        @Param("requestTimeoutMs") long requestTimeoutMs,
        @Param("retryCount") int retryCount,
        @Param("dailyTokenQuota") long dailyTokenQuota,
        @Param("enabled") boolean enabled
    );

    int updateClearingConnectionTest(
        @Param("id") long id,
        @Param("provider") String provider,
        @Param("baseUrl") String baseUrl,
        @Param("model") String model,
        @Param("apiKeyCiphertext") String apiKeyCiphertext,
        @Param("temperature") double temperature,
        @Param("maxOutputTokens") int maxOutputTokens,
        @Param("requestTimeoutMs") long requestTimeoutMs,
        @Param("retryCount") int retryCount,
        @Param("dailyTokenQuota") long dailyTokenQuota,
        @Param("enabled") boolean enabled
    );

    int insert(
        @Param("id") long id,
        @Param("provider") String provider,
        @Param("baseUrl") String baseUrl,
        @Param("model") String model,
        @Param("apiKeyCiphertext") String apiKeyCiphertext,
        @Param("temperature") double temperature,
        @Param("maxOutputTokens") int maxOutputTokens,
        @Param("requestTimeoutMs") long requestTimeoutMs,
        @Param("retryCount") int retryCount,
        @Param("dailyTokenQuota") long dailyTokenQuota,
        @Param("enabled") boolean enabled
    );

    int updateConnectionTest(@Param("id") long id, @Param("status") String status);
}
