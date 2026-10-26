package com.feng.dsagent.mail.mapper;

import com.feng.dsagent.mail.entity.MailConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：邮件配置单行的持久化访问。
 */
@Mapper
public interface MailConfigMapper {

    MailConfigEntity selectById(@Param("id") long id);

    int update(MailConfigEntity config);

    int insert(MailConfigEntity config);

    int updateConnectionTest(@Param("id") long id, @Param("status") String status);
}
