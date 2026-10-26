package com.feng.dsagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MyBatisConfigurationSmokeTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    public void sqlSessionFactoryIsAvailable() {
        assertThat(sqlSessionFactory).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration()).isNotNull();
        // Map-based studyroom SQL keeps snake_case labels (JdbcTemplate-era keys).
        assertThat(sqlSessionFactory.getConfiguration().isMapUnderscoreToCamelCase()).isFalse();
    }
}
