package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SandboxConfigService {

    private final SandboxConfigRepository repository;
    private final SandboxConfigUrlValidator urlValidator;
    private final SandboxConnectionTester connectionTester;
    private final SandboxConfigRuntimeSettingsSource runtimeSettings;

    SandboxConfigService(
        SandboxConfigRepository repository,
        SandboxConfigUrlValidator urlValidator,
        SandboxConnectionTester connectionTester,
        SandboxConfigRuntimeSettingsSource runtimeSettings
    ) {
        this.repository = repository;
        this.urlValidator = urlValidator;
        this.connectionTester = connectionTester;
        this.runtimeSettings = runtimeSettings;
    }

    public SandboxConfigController.SandboxConfigCapabilityView capability() {
        Optional<SandboxConfigRepository.StoredSandboxConfig> stored = repository.find();
        if (stored.isEmpty()) {
            return new SandboxConfigController.SandboxConfigCapabilityView(
                false,
                "NOT_CONFIGURED",
                null,
                runtimeView(runtimeSettings.current().orElse(null), "environment")
            );
        }
        SandboxConfigRepository.StoredSandboxConfig persisted = stored.get();
        SandboxProvider provider = SandboxProvider.fromApiName(persisted.provider()).orElse(null);
        if (provider == null) {
            return new SandboxConfigController.SandboxConfigCapabilityView(
                false,
                "SANDBOX_CONFIG_INVALID",
                null,
                runtimeView(null, "persisted")
            );
        }
        try {
            urlValidator.resolve(persisted.baseUrl());
        } catch (ApiException ignored) {
            return new SandboxConfigController.SandboxConfigCapabilityView(
                false,
                "SANDBOX_CONFIG_INVALID",
                null,
                runtimeView(null, "persisted", provider)
            );
        }
        SandboxConfigController.SandboxConfigView view = view(persisted);
        return new SandboxConfigController.SandboxConfigCapabilityView(
            persisted.enabled(),
            persisted.enabled() ? null : "PERSISTED_CONFIGURATION_DISABLED",
            view,
            runtimeView(
                new SandboxRuntimeSettings(provider, persisted.baseUrl(), persisted.enabled()),
                "persisted"
            )
        );
    }

    private SandboxConfigController.SandboxRuntimeStatusView runtimeView(
        SandboxRuntimeSettings settings,
        String source
    ) {
        return runtimeView(settings, source, settings == null ? null : settings.provider());
    }

    private SandboxConfigController.SandboxRuntimeStatusView runtimeView(
        SandboxRuntimeSettings settings,
        String source,
        SandboxProvider provider
    ) {
        return new SandboxConfigController.SandboxRuntimeStatusView(
            settings != null && settings.enabled(),
            source,
            provider == null ? "UNKNOWN" : provider.name(),
            null
        );
    }

    @Transactional
    SandboxConfigController.SandboxConfigView update(
        SandboxConfigController.UpdateSandboxConfigRequest request,
        AuthenticatedUser actor,
        String requestId
    ) {
        SandboxProvider provider = SandboxProvider.fromApiName(request.provider())
            .orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "SANDBOX_CONFIG_PROVIDER_UNSUPPORTED",
                "仅支持 Piston 或 Judge0 沙箱"
            ));
        URI baseUrl = urlValidator.validate(request.baseUrl());
        SandboxConfigRepository.StoredSandboxConfig before = repository.find().orElse(null);
        SandboxConfigRepository.StoredSandboxConfig stored = repository.save(provider, baseUrl.toString(), request.enabled());
        repository.appendAuditEvent(
            actor.userId(),
            "SANDBOX_CONFIG_UPDATED",
            "SUCCESS",
            requestId,
            summary(before),
            summary(stored)
        );
        return view(stored);
    }

    @Transactional
    SandboxConfigController.SandboxConnectionTestView testConnection(AuthenticatedUser actor, String requestId) {
        SandboxConfigRepository.StoredSandboxConfig stored = repository.find().orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "SANDBOX_CONFIG_NOT_CONFIGURED",
            "尚未保存沙箱配置"
        ));
        if (!stored.enabled()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "SANDBOX_CONFIG_DISABLED",
                "当前沙箱配置已停用"
            );
        }
        SandboxProvider provider = SandboxProvider.fromApiName(stored.provider()).orElseThrow(() -> new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SANDBOX_CONFIG_INVALID",
            "已保存的沙箱提供商无效"
        ));
        URI baseUrl = urlValidator.validate(stored.baseUrl());
        SandboxConnectionResult result;
        try {
            result = connectionTester.test(new SandboxRuntimeSettings(provider, baseUrl.toString(), true));
        } catch (RuntimeException ignored) {
            result = new SandboxConnectionResult(false, "CONNECTION_FAILED");
        }
        String code = result.code() == null || !result.code().matches("[A-Z0-9_]{1,64}")
            ? "CONNECTION_FAILED"
            : result.code();
        SandboxConfigRepository.StoredSandboxConfig after = repository.recordConnectionTest(code);
        repository.appendAuditEvent(
            actor.userId(),
            "SANDBOX_CONFIG_CONNECTION_TESTED",
            result.connected() ? "SUCCESS" : "FAILED",
            requestId,
            summary(stored),
            summary(after)
        );
        return new SandboxConfigController.SandboxConnectionTestView(result.connected(), code);
    }

    private SandboxConfigController.SandboxConfigView view(SandboxConfigRepository.StoredSandboxConfig stored) {
        return new SandboxConfigController.SandboxConfigView(
            stored.provider(),
            stored.baseUrl(),
            stored.enabled(),
            stored.lastConnectionTestStatus(),
            stored.lastConnectionTestedAt(),
            stored.updatedAt()
        );
    }

    private String summary(SandboxConfigRepository.StoredSandboxConfig stored) {
        if (stored == null) {
            return "state=UNCONFIGURED";
        }
        return "provider=" + stored.provider()
            + ";enabled=" + stored.enabled()
            + ";baseUrlHost=" + host(stored.baseUrl())
            + ";lastConnectionTestStatus=" + (stored.lastConnectionTestStatus() == null
                ? "NONE"
                : stored.lastConnectionTestStatus());
    }

    private String host(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "UNKNOWN" : host;
        } catch (IllegalArgumentException ignored) {
            return "UNKNOWN";
        }
    }
}
