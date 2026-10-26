package com.feng.dsagent.modelconfig.support;

import com.feng.dsagent.common.exception.BizException;
import java.time.Duration;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

import com.feng.dsagent.modelconfig.exception.ModelConfigRuntimeUnavailableException;
import com.feng.dsagent.modelconfig.domain.ModelConfigKeyBinding;
import com.feng.dsagent.modelconfig.domain.ModelConfigResolvedTarget;
import com.feng.dsagent.modelconfig.domain.ModelConfigRuntimeSettings;
import com.feng.dsagent.modelconfig.entity.ModelConfigEntity;
import com.feng.dsagent.modelconfig.mapper.ModelConfigMapper;
import com.feng.dsagent.modelconfig.service.ModelConfigService;
@Component
public class DatabaseModelConfigRuntimeSettingsSource implements ModelConfigRuntimeSettingsSource {

    private final ModelConfigMapper mapper;
    private final ModelConfigMasterKeySource masterKeySource;
    private final ModelConfigCrypto crypto;
    private final ModelConfigUrlValidator urlValidator;

    public DatabaseModelConfigRuntimeSettingsSource(
        ModelConfigMapper mapper,
        ModelConfigMasterKeySource masterKeySource,
        ModelConfigCrypto crypto,
        ModelConfigUrlValidator urlValidator
    ) {
        this.mapper = mapper;
        this.masterKeySource = masterKeySource;
        this.crypto = crypto;
        this.urlValidator = urlValidator;
    }

    @Override
    public Optional<ModelConfigRuntimeSettings> current() {
        Optional<ModelConfigEntity> stored = Optional.ofNullable(mapper.selectById(ModelConfigService.CONFIGURATION_ID));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!stored.get().enabled() || stored.get().dailyTokenQuota() <= 0) {
            throw new ModelConfigRuntimeUnavailableException();
        }
        SecretKey masterKey = masterKeySource.masterKey().orElseThrow(ModelConfigRuntimeUnavailableException::new);
        try {
            ModelConfigResolvedTarget target = urlValidator.resolve(stored.get().baseUrl());
            String apiKey = crypto.decrypt(
                stored.get().apiKeyCiphertext(),
                masterKey,
                ModelConfigKeyBinding.forConfiguration(
                    stored.get().id(),
                    stored.get().provider(),
                    stored.get().baseUrl()
                )
            );
            return Optional.of(new ModelConfigRuntimeSettings(
                stored.get().provider(),
                target,
                stored.get().model(),
                apiKey,
                stored.get().temperature(),
                stored.get().maxOutputTokens(),
                Duration.ofMillis(stored.get().requestTimeoutMs()),
                stored.get().retryCount(),
                stored.get().dailyTokenQuota()
            ));
        } catch (ModelConfigCrypto.CryptoFailure | BizException error) {
            throw new ModelConfigRuntimeUnavailableException();
        }
    }
}
