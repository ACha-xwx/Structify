package com.feng.dsagent.mail.support;

import java.util.Optional;
import javax.crypto.SecretKey;

public interface MailConfigMasterKeySource {
    public Optional<SecretKey> masterKey();
}
