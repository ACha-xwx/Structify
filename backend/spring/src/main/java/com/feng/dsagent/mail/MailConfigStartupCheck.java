package com.feng.dsagent.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports mail readiness at boot instead of at the first learner sign-up.
 *
 * The SMTP password is sealed with AES-GCM under the deployment's master key, bound to the
 * connection identity. When that key drifts (rotated, lost with an old env file, or never
 * carried into a new deployment), the stored config stays "enabled" in the database while
 * every verification-code request fails with a 503 - which is how a broken mail pipeline
 * hides until a tester tries to register. This check logs the exact remedy at startup.
 */
@Component
final class MailConfigStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(MailConfigStartupCheck.class);

    private final MailConfigRepository repository;
    private final MailConfigMasterKeySource masterKeySource;
    private final MailConfigCrypto crypto;

    MailConfigStartupCheck(
        MailConfigRepository repository,
        MailConfigMasterKeySource masterKeySource,
        MailConfigCrypto crypto
    ) {
        this.repository = repository;
        this.masterKeySource = masterKeySource;
        this.crypto = crypto;
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        MailConfigRepository.StoredMailConfig stored = repository.find().orElse(null);
        if (stored == null || !stored.enabled()) {
            return;
        }
        String host = stored.smtpHost();
        var key = masterKeySource.masterKey();
        if (key.isEmpty()) {
            log.warn("MAIL CONFIG 未就绪：已启用指向 {} 的 SMTP 配置，但 MAIL_CONFIG_MASTER_KEY（或其回退）缺失，"
                + "验证码邮件会以 503 失败。请在部署环境设置该密钥，或在管理端重新保存邮件配置。", host);
            return;
        }
        try {
            crypto.decrypt(stored.smtpPasswordCiphertext(), key.get(), new MailConfigKeyBinding(
                stored.id(), stored.smtpHost(), stored.smtpPort(), stored.securityMode(), stored.smtpUsername()
            ));
        } catch (MailConfigCrypto.CryptoFailure error) {
            log.warn("MAIL CONFIG 未就绪：{} 的 SMTP 密码密文无法用当前 MAIL_CONFIG_MASTER_KEY 解密"
                + "（配置保存于 {}，密钥可能在保存后被更换）。在管理端\"邮件配置\"里重新输入并保存 SMTP 密码，"
                + "或恢复加密时所用的主密钥，否则验证码邮件会以 503 失败。", host, stored.updatedAt());
        }
    }
}
