package com.feng.dsagent.compiler.service;

import com.feng.dsagent.compiler.converter.SandboxConfigConverter;
import com.feng.dsagent.compiler.support.SandboxConfigRuntimeSettingsSource;
import com.feng.dsagent.compiler.support.SandboxConfigUrlValidator;
import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.compiler.entity.SandboxConfigEntity;
import com.feng.dsagent.compiler.mapper.SandboxConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import com.feng.dsagent.compiler.domain.SandboxConnectionResult;
import com.feng.dsagent.compiler.support.SandboxConnectionTester;
import com.feng.dsagent.compiler.domain.SandboxRuntimeSettings;
import com.feng.dsagent.compiler.domain.SandboxProvider;

import com.feng.dsagent.compiler.error.CompilerErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.feng.dsagent.common.vo.ConnectionTestVO;
import com.feng.dsagent.compiler.dto.UpdateSandboxConfigDTO;
import com.feng.dsagent.compiler.vo.SandboxConfigCapabilityVO;
import com.feng.dsagent.compiler.vo.SandboxConfigVO;
import com.feng.dsagent.compiler.vo.SandboxRuntimeStatusVO;

@Service
public class SandboxConfigService {

    public static final long CONFIGURATION_ID = 1L;

    private final SandboxConfigMapper mapper;
    private final SandboxConfigConverter sandboxConfigConverter;
    private final AdminAuditService auditService;
    private final SandboxConfigUrlValidator urlValidator;
    private final SandboxConnectionTester connectionTester;
    private final SandboxConfigRuntimeSettingsSource runtimeSettings;

    public SandboxConfigService(
        SandboxConfigMapper mapper,
        SandboxConfigConverter sandboxConfigConverter,
        AdminAuditService auditService,
        SandboxConfigUrlValidator urlValidator,
        SandboxConnectionTester connectionTester,
        SandboxConfigRuntimeSettingsSource runtimeSettings
    ) {
        this.mapper = mapper;
        this.sandboxConfigConverter = sandboxConfigConverter;
        this.auditService = auditService;
        this.urlValidator = urlValidator;
        this.connectionTester = connectionTester;
        this.runtimeSettings = runtimeSettings;
    }

    public SandboxConfigCapabilityVO capability() {
        Optional<SandboxConfigEntity> stored = find();
        if (stored.isEmpty()) {
            return new SandboxConfigCapabilityVO(
                false,
                "NOT_CONFIGURED",
                null,
                runtimeView(runtimeSettings.current().orElse(null), "environment")
            );
        }
        SandboxConfigEntity persisted = stored.get();
        SandboxProvider provider = SandboxProvider.fromApiName(persisted.provider()).orElse(null);
        if (provider == null) {
            return new SandboxConfigCapabilityVO(
                false,
                "SANDBOX_CONFIG_INVALID",
                null,
                runtimeView(null, "persisted")
            );
        }
        try {
            urlValidator.resolve(persisted.baseUrl());
        } catch (BizException ignored) {
            return new SandboxConfigCapabilityVO(
                false,
                "SANDBOX_CONFIG_INVALID",
                null,
                runtimeView(null, "persisted", provider)
            );
        }
        SandboxConfigVO view = view(persisted);
        return new SandboxConfigCapabilityVO(
            persisted.enabled(),
            persisted.enabled() ? null : "PERSISTED_CONFIGURATION_DISABLED",
            view,
            runtimeView(
                new SandboxRuntimeSettings(provider, persisted.baseUrl(), persisted.enabled()),
                "persisted"
            )
        );
    }

    private SandboxRuntimeStatusVO runtimeView(
        SandboxRuntimeSettings settings,
        String source
    ) {
        return runtimeView(settings, source, settings == null ? null : settings.provider());
    }

    private SandboxRuntimeStatusVO runtimeView(
        SandboxRuntimeSettings settings,
        String source,
        SandboxProvider provider
    ) {
        return new SandboxRuntimeStatusVO(
            settings != null && settings.enabled(),
            source,
            provider == null ? "UNKNOWN" : provider.name(),
            null
        );
    }

    @Transactional
    public SandboxConfigVO update(
        UpdateSandboxConfigDTO request,
        AuthenticatedUser actor,
        String requestId
    ) {
        SandboxProvider provider = SandboxProvider.fromApiName(request.provider())
            .orElseThrow(() -> CompilerErrorCode.SANDBOX_CONFIG_PROVIDER_UNSUPPORTED.toException());
        URI baseUrl = urlValidator.validate(request.baseUrl());
        SandboxConfigEntity before = find().orElse(null);
        SandboxConfigEntity stored = save(provider, baseUrl.toString(), request.enabled());
        auditService.record(
            actor.userId(),
            "SANDBOX_CONFIG_UPDATED",
            "SANDBOX_CONFIG",
            "1",
            "SUCCESS",
            safeRequestId(requestId),
            summary(before),
            summary(stored)
        );
        return view(stored);
    }

    @Transactional
    public ConnectionTestVO testConnection(AuthenticatedUser actor, String requestId) {
        SandboxConfigEntity stored = find().orElseThrow(() -> CompilerErrorCode.SANDBOX_CONFIG_NOT_CONFIGURED.toException());
        if (!stored.enabled()) {
            throw CompilerErrorCode.SANDBOX_CONFIG_DISABLED.toException();
        }
        SandboxProvider provider = SandboxProvider.fromApiName(stored.provider()).orElseThrow(() -> CompilerErrorCode.SANDBOX_CONFIG_INVALID.toException());
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
        SandboxConfigEntity after = recordConnectionTest(code);
        auditService.record(
            actor.userId(),
            "SANDBOX_CONFIG_CONNECTION_TESTED",
            "SANDBOX_CONFIG",
            "1",
            result.connected() ? "SUCCESS" : "FAILED",
            safeRequestId(requestId),
            summary(stored),
            summary(after)
        );
        return new ConnectionTestVO(result.connected(), code);
    }


    private Optional<SandboxConfigEntity> find() {
        return Optional.ofNullable(mapper.selectById(CONFIGURATION_ID));
    }

    private SandboxConfigEntity save(SandboxProvider provider, String baseUrl, boolean enabled) {
        int updated = mapper.update(CONFIGURATION_ID, provider.name(), baseUrl, enabled);
        if (updated == 0) {
            try {
                mapper.insert(CONFIGURATION_ID, provider.name(), baseUrl, enabled);
            } catch (DuplicateKeyException ignored) {
                mapper.update(CONFIGURATION_ID, provider.name(), baseUrl, enabled);
            }
        }
        return find().orElseThrow(() -> new IllegalStateException("Sandbox configuration was not persisted"));
    }

    private SandboxConfigEntity recordConnectionTest(String status) {
        mapper.updateConnectionTest(CONFIGURATION_ID, status);
        return find().orElseThrow(() -> new IllegalStateException("Sandbox configuration was not persisted"));
    }

    private String safeRequestId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, Math.min(128, value.length()));
    }

    private SandboxConfigVO view(SandboxConfigEntity stored) {
        return sandboxConfigConverter.toView(stored);
    }

    private String summary(SandboxConfigEntity stored) {
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
