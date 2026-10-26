package com.feng.dsagent.modelconfig.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.modelconfig.converter.ModelConfigConverter;
import com.feng.dsagent.modelconfig.domain.ModelConfigGenerationControls;
import com.feng.dsagent.modelconfig.domain.ModelConfigConnectionResult;
import com.feng.dsagent.modelconfig.support.ModelConfigConnectionTester;
import com.feng.dsagent.modelconfig.support.ModelConfigMasterKeySource;
import com.feng.dsagent.modelconfig.domain.ModelConfigResolvedTarget;
import com.feng.dsagent.modelconfig.support.ModelGenerationReadiness;
import com.feng.dsagent.modelconfig.support.ModelConfigUrlValidator;
import com.feng.dsagent.modelconfig.domain.ModelConfigConnection;
import com.feng.dsagent.modelconfig.domain.ModelConfigKeyBinding;
import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.modelconfig.entity.ModelConfigEntity;
import com.feng.dsagent.modelconfig.mapper.ModelConfigMapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import com.feng.dsagent.modelconfig.support.ModelConfigCrypto;
import com.feng.dsagent.modelconfig.error.ModelConfigErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.transaction.annotation.Transactional;
import com.feng.dsagent.common.vo.ConfigCapabilityVO;
import com.feng.dsagent.common.vo.ConnectionTestVO;
import com.feng.dsagent.modelconfig.dto.UpdateModelConfigDTO;
import com.feng.dsagent.modelconfig.vo.ModelConfigVO;

/**
 * 职责：LLM 连接配置的加解密存储、连通性测试与生成就绪判断。
 */
@Service
public class ModelConfigService {

    private final ModelConfigMasterKeySource masterKeySource;
    private final ModelConfigMapper mapper;
    private final ModelConfigConverter modelConfigConverter;
    private final AdminAuditService auditService;
    private final ModelConfigCrypto crypto;
    private final ModelConfigUrlValidator urlValidator;
    private final ModelConfigConnectionTester connectionTester;
    private final ModelGenerationReadiness generationReadiness;

    public static final long CONFIGURATION_ID = 1L;

    public ModelConfigService(
        ModelConfigMasterKeySource masterKeySource,
        ModelConfigMapper mapper,
        ModelConfigConverter modelConfigConverter,
        AdminAuditService auditService,
        ModelConfigCrypto crypto,
        ModelConfigUrlValidator urlValidator,
        ModelConfigConnectionTester connectionTester,
        ModelGenerationReadiness generationReadiness
    ) {
        this.masterKeySource = masterKeySource;
        this.mapper = mapper;
        this.modelConfigConverter = modelConfigConverter;
        this.auditService = auditService;
        this.crypto = crypto;
        this.urlValidator = urlValidator;
        this.connectionTester = connectionTester;
        this.generationReadiness = generationReadiness;
    }

    public ConfigCapabilityVO<ModelConfigVO> capability() {
        CapabilityState accessState = configurationAccessState();
        if (!accessState.available()) {
            return new ConfigCapabilityVO<>(false, accessState.reason(), null);
        }
        CapabilityState generationState = capabilityState();
        return new ConfigCapabilityVO<>(
            generationState.available(),
            generationState.reason(),
            view(find().orElseThrow())
        );
    }

    private CapabilityState configurationAccessState() {
        Optional<SecretKey> masterKey = masterKeySource.masterKey();
        if (masterKey.isEmpty()) {
            return new CapabilityState(false, "MASTER_KEY_UNAVAILABLE");
        }
        Optional<ModelConfigEntity> stored = find();
        if (stored.isEmpty()) {
            return new CapabilityState(false, "NOT_CONFIGURED");
        }
        try {
            crypto.decrypt(stored.get().apiKeyCiphertext(), masterKey.get(), keyBinding(stored.get()));
            return new CapabilityState(true, null);
        } catch (ModelConfigCrypto.CryptoFailure ignored) {
            return new CapabilityState(false, "MODEL_CONFIG_UNAVAILABLE");
        }
    }

    public CapabilityState capabilityState() {
        Optional<SecretKey> masterKey = masterKeySource.masterKey();
        if (masterKey.isEmpty()) {
            return new CapabilityState(false, "MASTER_KEY_UNAVAILABLE");
        }
        Optional<ModelGenerationReadiness.State> persisted = generationReadiness.persisted();
        if (persisted.isEmpty()) {
            return new CapabilityState(false, "NOT_CONFIGURED");
        }
        return capabilityState(persisted.get());
    }

    private CapabilityState capabilityState(ModelGenerationReadiness.State state) {
        return switch (state.reason()) {
            case PERSISTED_CONFIGURATION_READY -> new CapabilityState(true, null);
            case PERSISTED_CONFIGURATION_DISABLED, PERSISTED_QUOTA_NOT_CONFIGURED ->
                new CapabilityState(false, state.reason().name());
            case PERSISTED_CONFIGURATION_UNAVAILABLE -> new CapabilityState(false, "MODEL_CONFIG_UNAVAILABLE");
            default -> new CapabilityState(false, "MODEL_CONFIG_UNAVAILABLE");
        };
    }

    @Transactional
    public ModelConfigVO update(
        UpdateModelConfigDTO request,
        AuthenticatedUser actor,
        String requestId
    ) {
        SecretKey masterKey = requiredMasterKey();
        URI baseUrl = urlValidator.validate(request.baseUrl());
        String provider = request.provider().strip();
        String model = request.model().strip();
        Optional<ModelConfigEntity> existing = find();
        ModelConfigGenerationControls controls = controls(request, existing);
        String suppliedApiKey = normalizeOptional(request.apiKey());
        boolean targetChanged = existing.isEmpty()
            || !existing.get().provider().equals(provider)
            || !existing.get().baseUrl().equals(baseUrl.toString());
        boolean connectionEvidenceInvalidated = suppliedApiKey != null
            || targetChanged
            || existing.isPresent() && !existing.get().model().equals(model);
        String ciphertext;
        if (suppliedApiKey == null) {
            if (targetChanged) {
                throw ModelConfigErrorCode.API_KEY_REQUIRED.toException();
            }
            ciphertext = existing.get().apiKeyCiphertext();
        } else {
            ciphertext = crypto.encrypt(
                suppliedApiKey,
                masterKey,
                ModelConfigKeyBinding.forConfiguration(CONFIGURATION_ID, provider, baseUrl.toString())
            );
        }
        ModelConfigEntity stored = save(
            provider,
            baseUrl.toString(),
            model,
            ciphertext,
            controls,
            connectionEvidenceInvalidated
        );
        appendAudit(actor, "MODEL_CONFIG_UPDATED", "SUCCESS", requestId, existing.orElse(null), stored);
        return view(stored);
    }

    @Transactional
    public ConnectionTestVO testConnection(AuthenticatedUser actor, String requestId) {
        SecretKey masterKey = requiredMasterKey();
        ModelConfigEntity stored = find().orElseThrow(() -> ModelConfigErrorCode.NOT_CONFIGURED.toException());
        ModelConfigResolvedTarget target = urlValidator.resolve(stored.baseUrl());
        String apiKey;
        try {
            apiKey = crypto.decrypt(stored.apiKeyCiphertext(), masterKey, keyBinding(stored));
        } catch (ModelConfigCrypto.CryptoFailure error) {
            throw ModelConfigErrorCode.UNAVAILABLE.toException();
        }
        ModelConfigConnectionResult result;
        long connectionTestStartedAt = System.nanoTime();
        try {
            result = connectionTester.test(new ModelConfigConnection(
                stored.provider(),
                target,
                stored.model(),
                apiKey
            ));
        } catch (RuntimeException ignored) {
            result = new ModelConfigConnectionResult(false, "CONNECTION_FAILED");
        }
        long connectionTestElapsedMs = Math.max(
            0L,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectionTestStartedAt)
        );
        String code = connectionCode(result);
        ModelConfigEntity after = recordConnectionTest(code);
        auditService.record(
            actor.userId(),
            "MODEL_CONFIG_CONNECTION_TESTED",
            "MODEL_CONFIG",
            "1",
            result.connected() ? "SUCCESS" : "FAILED",
            safeRequestId(requestId),
            summary(stored),
            summary(after) + ";connectionTestElapsedMs=" + connectionTestElapsedMs
                + ";credentialRedacted=true"
        );
        return new ConnectionTestVO(result.connected(), code);
    }

    private SecretKey requiredMasterKey() {
        return masterKeySource.masterKey().orElseThrow(ModelConfigErrorCode.UNAVAILABLE::toException);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private ModelConfigGenerationControls controls(
        UpdateModelConfigDTO request,
        Optional<ModelConfigEntity> existing
    ) {
        ModelConfigGenerationControls fallback = existing
            .map(ModelConfigService::generationControls)
            .orElseGet(ModelConfigGenerationControls::defaults);
        double temperature = request.temperature() == null ? fallback.temperature() : request.temperature();
        int maxOutputTokens = request.maxOutputTokens() == null ? fallback.maxOutputTokens() : request.maxOutputTokens();
        long requestTimeoutMs = request.requestTimeoutMs() == null ? fallback.requestTimeoutMs() : request.requestTimeoutMs();
        int retryCount = request.retryCount() == null ? fallback.retryCount() : request.retryCount();
        long dailyTokenQuota = request.dailyTokenQuota() == null ? fallback.dailyTokenQuota() : request.dailyTokenQuota();
        boolean enabled = request.enabled() == null ? fallback.enabled() : request.enabled();
        if (!Double.isFinite(temperature)
            || temperature < 0.0
            || temperature > 2.0
            || maxOutputTokens < 1
            || maxOutputTokens > ModelConfigGenerationControls.MAX_OUTPUT_TOKENS
            || requestTimeoutMs < 1_000
            || requestTimeoutMs > 120_000
            || retryCount < 0
            || retryCount > 5
            || dailyTokenQuota < 0
            || dailyTokenQuota > 10_000_000) {
            throw ModelConfigErrorCode.SETTINGS_INVALID.toException();
        }
        if (enabled && dailyTokenQuota == 0) {
            throw ModelConfigErrorCode.QUOTA_REQUIRED.toException();
        }
        return new ModelConfigGenerationControls(
            temperature,
            maxOutputTokens,
            requestTimeoutMs,
            retryCount,
            dailyTokenQuota,
            enabled
        );
    }

    private String connectionCode(ModelConfigConnectionResult result) {
        if (result == null || result.code() == null || !result.code().matches("[A-Z0-9_]{1,64}")) {
            return "CONNECTION_FAILED";
        }
        return result.code();
    }

    private void appendAudit(
        AuthenticatedUser actor,
        String action,
        String result,
        String requestId,
        ModelConfigEntity before,
        ModelConfigEntity after
    ) {
        auditService.record(
            actor.userId(),
            action,
            "MODEL_CONFIG",
            "1",
            result,
            safeRequestId(requestId),
            summary(before),
            summary(after)
        );
    }

    private Optional<ModelConfigEntity> find() {
        return Optional.ofNullable(mapper.selectById(CONFIGURATION_ID));
    }

    private ModelConfigEntity save(
        String provider,
        String baseUrl,
        String model,
        String apiKeyCiphertext,
        ModelConfigGenerationControls controls,
        boolean clearConnectionTest
    ) {
        int updated = clearConnectionTest
            ? mapper.updateClearingConnectionTest(
                CONFIGURATION_ID, provider, baseUrl, model, apiKeyCiphertext,
                controls.temperature(), controls.maxOutputTokens(), controls.requestTimeoutMs(),
                controls.retryCount(), controls.dailyTokenQuota(), controls.enabled())
            : mapper.updateKeepingConnectionTest(
                CONFIGURATION_ID, provider, baseUrl, model, apiKeyCiphertext,
                controls.temperature(), controls.maxOutputTokens(), controls.requestTimeoutMs(),
                controls.retryCount(), controls.dailyTokenQuota(), controls.enabled());
        if (updated == 0) {
            try {
                mapper.insert(
                    CONFIGURATION_ID, provider, baseUrl, model, apiKeyCiphertext,
                    controls.temperature(), controls.maxOutputTokens(), controls.requestTimeoutMs(),
                    controls.retryCount(), controls.dailyTokenQuota(), controls.enabled());
            } catch (DuplicateKeyException ignored) {
                if (clearConnectionTest) {
                    mapper.updateClearingConnectionTest(
                        CONFIGURATION_ID, provider, baseUrl, model, apiKeyCiphertext,
                        controls.temperature(), controls.maxOutputTokens(), controls.requestTimeoutMs(),
                        controls.retryCount(), controls.dailyTokenQuota(), controls.enabled());
                } else {
                    mapper.updateKeepingConnectionTest(
                        CONFIGURATION_ID, provider, baseUrl, model, apiKeyCiphertext,
                        controls.temperature(), controls.maxOutputTokens(), controls.requestTimeoutMs(),
                        controls.retryCount(), controls.dailyTokenQuota(), controls.enabled());
                }
            }
        }
        return find().orElseThrow(() -> new IllegalStateException("Model configuration was not persisted"));
    }

    private ModelConfigEntity recordConnectionTest(String status) {
        mapper.updateConnectionTest(CONFIGURATION_ID, status);
        return find().orElseThrow(() -> new IllegalStateException("Model configuration was not persisted"));
    }

    private String safeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "";
        }
        return requestId.substring(0, Math.min(requestId.length(), 128));
    }

    private String summary(ModelConfigEntity stored) {
        if (stored == null) {
            return "state=UNCONFIGURED";
        }
        return "configured=true;enabled=" + stored.enabled()
            + ";temperature=" + stored.temperature()
            + ";maxOutputTokens=" + stored.maxOutputTokens()
            + ";requestTimeoutMs=" + stored.requestTimeoutMs()
            + ";retryCount=" + stored.retryCount()
            + ";dailyTokenQuota=" + stored.dailyTokenQuota()
            + ";lastConnectionTestStatus=" + (stored.lastConnectionTestStatus() == null
                ? "NONE"
                : stored.lastConnectionTestStatus());
    }

    private ModelConfigVO view(ModelConfigEntity stored) {
        return modelConfigConverter.toView(stored);
    }

    private static ModelConfigKeyBinding keyBinding(ModelConfigEntity stored) {
        return ModelConfigKeyBinding.forConfiguration(stored.id(), stored.provider(), stored.baseUrl());
    }

    private static ModelConfigGenerationControls generationControls(ModelConfigEntity stored) {
        return new ModelConfigGenerationControls(
            stored.temperature(),
            stored.maxOutputTokens(),
            stored.requestTimeoutMs(),
            stored.retryCount(),
            stored.dailyTokenQuota(),
            stored.enabled()
        );
    }

    public record CapabilityState(boolean available, String reason) {
    }
}
