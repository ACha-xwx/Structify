package com.feng.dsagent.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper scan for capability packages under {@code com.feng.dsagent.*.mapper}.
 * Only interfaces annotated with {@link Mapper} are registered. SQL lives in
 * {@code classpath:mapper/<capability>/*Mapper.xml}. See
 * {@code docs/architecture/backend-standards.md}.
 */
@Configuration
@MapperScan(basePackages = "com.feng.dsagent", annotationClass = Mapper.class)
public class MyBatisConfig {
}
