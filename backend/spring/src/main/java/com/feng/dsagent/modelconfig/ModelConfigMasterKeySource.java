package com.feng.dsagent.modelconfig;

import java.util.Optional;
import javax.crypto.SecretKey;

public interface ModelConfigMasterKeySource {

    public Optional<SecretKey> masterKey();
}
