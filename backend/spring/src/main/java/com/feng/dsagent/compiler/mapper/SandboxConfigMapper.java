package com.feng.dsagent.compiler.mapper;

import com.feng.dsagent.compiler.entity.SandboxConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SandboxConfigMapper {

    SandboxConfigEntity selectById(@Param("id") long id);

    int update(
        @Param("id") long id,
        @Param("provider") String provider,
        @Param("baseUrl") String baseUrl,
        @Param("enabled") boolean enabled
    );

    int insert(
        @Param("id") long id,
        @Param("provider") String provider,
        @Param("baseUrl") String baseUrl,
        @Param("enabled") boolean enabled
    );

    int updateConnectionTest(@Param("id") long id, @Param("status") String status);
}
