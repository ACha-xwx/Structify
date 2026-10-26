package com.feng.dsagent.mail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.feng.dsagent.mail.domain.MailConfigKeyBinding;
import com.feng.dsagent.mail.entity.MailConfigEntity;
import com.feng.dsagent.mail.mapper.MailConfigMapper;
import com.feng.dsagent.mail.service.MailConfigService;
import com.feng.dsagent.mail.support.MailConfigCrypto;
import com.feng.dsagent.mail.support.MailConfigMasterKeySource;
import com.feng.dsagent.mail.support.MailConfigStartupCheck;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * The boot-time mail check must never throw and must judge readiness from the real crypto
 * path - a wrong master key used to surface only as a 503 on the first sign-up attempt.
 */
class MailConfigStartupCheckTest {

    private final MailConfigMapper mapper = mock(MailConfigMapper.class);
    private final MailConfigMasterKeySource masterKeySource = mock(MailConfigMasterKeySource.class);
    private final MailConfigCrypto crypto = new MailConfigCrypto();
    private final MailConfigStartupCheck check = new MailConfigStartupCheck(mapper, masterKeySource, crypto);

    @Test
    void ignoresDisabledOrMissingConfig() {
        when(mapper.selectById(MailConfigService.CONFIGURATION_ID)).thenReturn(null);
        check.check();

        when(mapper.selectById(MailConfigService.CONFIGURATION_ID)).thenReturn(config(false, "v1:a:b"));
        check.check();

        verifyNoInteractions(masterKeySource);
    }

    @Test
    void passesWhenTheStoredPasswordStillDecrypts() throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        String ciphertext = crypto.encrypt("smtp-secret", key, new MailConfigKeyBinding(1L, "smtp.example.com", 587, "STARTTLS", "user"));
        when(mapper.selectById(MailConfigService.CONFIGURATION_ID)).thenReturn(config(true, ciphertext));
        when(masterKeySource.masterKey()).thenReturn(Optional.of(key));

        check.check();
    }

    @Test
    void staysSilentButAliveWhenTheKeyCannotDecryptTheStoredPassword() throws Exception {
        SecretKey savedWith = KeyGenerator.getInstance("AES").generateKey();
        SecretKey rotatedTo = KeyGenerator.getInstance("AES").generateKey();
        String ciphertext = crypto.encrypt("smtp-secret", savedWith, new MailConfigKeyBinding(1L, "smtp.example.com", 587, "STARTTLS", "user"));
        when(mapper.selectById(MailConfigService.CONFIGURATION_ID)).thenReturn(config(true, ciphertext));
        when(masterKeySource.masterKey()).thenReturn(Optional.of(rotatedTo));

        // The drift is exactly what production hit: config stays enabled, decryption fails,
        // and the remedy is a WARN log - never an exception during startup.
        check.check();
    }

    @Test
    void staysSilentWhenNoMasterKeyIsConfigured() {
        when(mapper.selectById(MailConfigService.CONFIGURATION_ID)).thenReturn(config(true, "v1:a:b"));
        when(masterKeySource.masterKey()).thenReturn(Optional.empty());

        check.check();
    }

    private static MailConfigEntity config(boolean enabled, String ciphertext) {
        return new MailConfigEntity(
            1L, "Structify", enabled, "smtp.example.com", 587, "STARTTLS", "user",
            ciphertext, "no-reply@example.com", "Structify", 12, 10, 60, 30,
            "subject", "<p>code</p>", "", null, Instant.parse("2026-08-17T10:01:40Z")
        );
    }
}
