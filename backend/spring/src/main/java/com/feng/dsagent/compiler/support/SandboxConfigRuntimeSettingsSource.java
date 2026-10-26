package com.feng.dsagent.compiler.support;

import java.util.Optional;
import com.feng.dsagent.common.exception.BizException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.feng.dsagent.compiler.config.CompilerProperties;
import com.feng.dsagent.compiler.domain.SandboxProvider;
import com.feng.dsagent.compiler.domain.SandboxRuntimeSettings;
import com.feng.dsagent.compiler.entity.SandboxConfigEntity;
import com.feng.dsagent.compiler.mapper.SandboxConfigMapper;
import com.feng.dsagent.compiler.service.SandboxConfigService;
@Component
public final class SandboxConfigRuntimeSettingsSource {

    private final SandboxConfigMapper mapper;
    private final CompilerProperties properties;
    private final SandboxConfigUrlValidator urlValidator;
    private final Supplier<Optional<SandboxRuntimeSettings>> fixedSettings;

    @Autowired
    public SandboxConfigRuntimeSettingsSource(
        SandboxConfigMapper mapper,
        CompilerProperties properties,
        SandboxConfigUrlValidator urlValidator
    ) {
        this.mapper = mapper;
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.fixedSettings = null;
    }

    public SandboxConfigRuntimeSettingsSource(SandboxConfigMapper mapper, CompilerProperties properties) {
        this(mapper, properties, null);
    }

    public SandboxConfigRuntimeSettingsSource(CompilerProperties properties) {
        this.mapper = null;
        this.properties = properties;
        this.urlValidator = null;
        this.fixedSettings = null;
    }

    public SandboxConfigRuntimeSettingsSource(SandboxRuntimeSettings settings) {
        this.mapper = null;
        this.properties = null;
        this.urlValidator = null;
        this.fixedSettings = () -> Optional.ofNullable(settings);
    }

    public Optional<SandboxRuntimeSettings> current() {
        if (fixedSettings != null) {
            return fixedSettings.get();
        }
        if (mapper != null) {
            Optional<SandboxConfigEntity> stored = Optional.ofNullable(mapper.selectById(SandboxConfigService.CONFIGURATION_ID));
            if (stored.isPresent()) {
                try {
                    String baseUrl = urlValidator == null
                        ? stored.get().baseUrl()
                        : urlValidator.validate(stored.get().baseUrl()).toString();
                    return SandboxProvider.fromApiName(stored.get().provider())
                        .map(provider -> new SandboxRuntimeSettings(provider, baseUrl, stored.get().enabled()));
                } catch (BizException ignored) {
                    return Optional.empty();
                }
            }
        }
        if (properties.configured()) {
            try {
                String baseUrl = urlValidator == null
                    ? properties.pistonBaseUrl().strip()
                    : urlValidator.validate(properties.pistonBaseUrl()).toString();
                return Optional.of(new SandboxRuntimeSettings(SandboxProvider.PISTON, baseUrl, true));
            } catch (BizException ignored) {
                return Optional.empty();
            }
        }
        String judge0BaseUrl = firstNonBlank(System.getenv("JUDGE0_BASE_URL"), System.getProperty("JUDGE0_BASE_URL"));
        if (judge0BaseUrl != null) {
            try {
                String baseUrl = urlValidator == null
                    ? judge0BaseUrl.strip()
                    : urlValidator.validate(judge0BaseUrl).toString();
                return Optional.of(new SandboxRuntimeSettings(SandboxProvider.JUDGE0, baseUrl, true));
            } catch (BizException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
