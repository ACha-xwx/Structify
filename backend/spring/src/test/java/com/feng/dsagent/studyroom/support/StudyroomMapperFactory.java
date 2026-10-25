package com.feng.dsagent.studyroom.support;

import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Opens MyBatis mappers against an ephemeral test DataSource. */
public final class StudyroomMapperFactory {
    private StudyroomMapperFactory() {}

    public static <T> T create(DataSource dataSource, Class<T> type) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:mapper/studyroom/*Mapper.xml"));
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = Objects.requireNonNull(factoryBean.getObject());
        return new SqlSessionTemplate(factory).getMapper(type);
    }
}
