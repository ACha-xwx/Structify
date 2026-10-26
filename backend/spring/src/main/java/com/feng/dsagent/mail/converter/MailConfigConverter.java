package com.feng.dsagent.mail.converter;

import com.feng.dsagent.mail.entity.MailConfigEntity;
import com.feng.dsagent.mail.vo.MailConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将邮件 SMTP 配置实体转换为对外 VO（含密码是否已配置派生字段）。
 */
@Mapper(componentModel = "spring")
public interface MailConfigConverter {

    @Mapping(
        target = "smtpPasswordConfigured",
        expression = "java(entity.smtpPasswordCiphertext() != null && !entity.smtpPasswordCiphertext().isBlank())"
    )
    MailConfigVO toView(MailConfigEntity entity);
}
