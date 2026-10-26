package com.feng.dsagent.modelconfig.support;

import com.feng.dsagent.aiquota.config.AiQuotaExecutionProperties;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.model.config.ModelProperties;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

import com.feng.dsagent.modelconfig.domain.ModelConfigKeyBinding;
import com.feng.dsagent.modelconfig.entity.ModelConfigEntity;
import com.feng.dsagent.modelconfig.mapper.ModelConfigMapper;
import com.feng.dsagent.modelconfig.service.ModelConfigService;
@Component
public final class ModelGenerationReadiness {

    private final ModelConfigMapper mapper;
    private final ModelConfigMasterKeySource masterKeySource;
    private final ModelConfigCrypto crypto;
    private final ModelConfigUrlValidator urlValidator;
    private final ModelProperties environment;
    private final AiQuotaExecutionProperties aiQuota;

    public ModelGenerationReadiness(
        ModelConfigMapper mapper,
        ModelConfigMasterKeySource masterKeySource,
        ModelConfigCrypto crypto,
        ModelConfigUrlValidator urlValidator,
        ModelProperties environment,
        AiQuotaExecutionProperties aiQuota
    ) {
        this.mapper = mapper;
        this.masterKeySource = masterKeySource;
        this.crypto = crypto;
        this.urlValidator = urlValidator;
        this.environment = environment;
        this.aiQuota = aiQuota;
    }

    public State current() {
        return persisted().orElseGet(this::environmentState);
    }

    public Optional<State> persisted() {
        return Optional.ofNullable(mapper.selectById(ModelConfigService.CONFIGURATION_ID)).map(this::persistedState);
    }

    private State persistedState(ModelConfigEntity stored) {
        if (!stored.enabled()) {
            return State.unavailable(Reason.PERSISTED_CONFIGURATION_DISABLED);
        }
        if (stored.dailyTokenQuota() <= 0) {
            return State.unavailable(Reason.PERSISTED_QUOTA_NOT_CONFIGURED);
        }
        SecretKey masterKey = masterKeySource.masterKey().orElse(null);
        if (masterKey == null || isBlank(stored.provider()) || isBlank(stored.model())) {
            return State.unavailable(Reason.PERSISTED_CONFIGURATION_UNAVAILABLE);
        }
        try {
            urlValidator.resolve(stored.baseUrl());
            String apiKey = crypto.decrypt(
                stored.apiKeyCiphertext(),
                masterKey,
                ModelConfigKeyBinding.forConfiguration(stored.id(), stored.provider(), stored.baseUrl())
            );
            if (isBlank(apiKey) || !PinnedHttpsTransport.isSafeHeaderValue(apiKey)) {
                return State.unavailable(Reason.PERSISTED_CONFIGURATION_UNAVAILABLE);
            }
            return State.eligible(Reason.PERSISTED_CONFIGURATION_READY, stored.dailyTokenQuota());
        } catch (ModelConfigCrypto.CryptoFailure | BizException error) {
            return State.unavailable(Reason.PERSISTED_CONFIGURATION_UNAVAILABLE);
        }
    }

    private State environmentState() {
        if (!isBlank(environment.apiKey())
            && !isBlank(environment.baseUrl())
            && !isBlank(environment.name())) {
            if (aiQuota.dailyTokenQuota() < 1) {
                return State.unavailable(Reason.ENVIRONMENT_QUOTA_NOT_CONFIGURED);
            }
            return State.eligible(Reason.ENVIRONMENT_CONFIGURATION_READY, aiQuota.dailyTokenQuota());
        }
        return State.unavailable(Reason.ENVIRONMENT_CONFIGURATION_INCOMPLETE);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Reason {
        PERSISTED_CONFIGURATION_READY,
        PERSISTED_CONFIGURATION_DISABLED,
        PERSISTED_QUOTA_NOT_CONFIGURED,
        PERSISTED_CONFIGURATION_UNAVAILABLE,
        ENVIRONMENT_CONFIGURATION_READY,
        ENVIRONMENT_QUOTA_NOT_CONFIGURED,
        ENVIRONMENT_CONFIGURATION_INCOMPLETE
    }

    public record State(boolean eligible, Reason reason, Long dailyTokenQuota) {

        private static State eligible(Reason reason, long dailyTokenQuota) {
            return new State(true, reason, dailyTokenQuota);
        }

        private static State unavailable(Reason reason) {
            return new State(false, reason, null);
        }
    }
}
