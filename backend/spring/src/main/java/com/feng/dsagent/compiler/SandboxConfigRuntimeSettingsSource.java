package com.feng.dsagent.compiler;

import java.util.Optional;
import com.feng.dsagent.common.ApiException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class SandboxConfigRuntimeSettingsSource {

    private final SandboxConfigRepository repository;
    private final CompilerProperties properties;
    private final SandboxConfigUrlValidator urlValidator;
    private final Supplier<Optional<SandboxRuntimeSettings>> fixedSettings;

    @Autowired
    public SandboxConfigRuntimeSettingsSource(
        SandboxConfigRepository repository,
        CompilerProperties properties,
        SandboxConfigUrlValidator urlValidator
    ) {
        this.repository = repository;
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.fixedSettings = null;
    }

    SandboxConfigRuntimeSettingsSource(SandboxConfigRepository repository, CompilerProperties properties) {
        this(repository, properties, null);
    }

    SandboxConfigRuntimeSettingsSource(CompilerProperties properties) {
        this.repository = null;
        this.properties = properties;
        this.urlValidator = null;
        this.fixedSettings = null;
    }

    SandboxConfigRuntimeSettingsSource(SandboxRuntimeSettings settings) {
        this.repository = null;
        this.properties = null;
        this.urlValidator = null;
        this.fixedSettings = () -> Optional.ofNullable(settings);
    }

    Optional<SandboxRuntimeSettings> current() {
        if (fixedSettings != null) {
            return fixedSettings.get();
        }
        if (repository != null) {
            Optional<SandboxConfigRepository.StoredSandboxConfig> stored = repository.find();
            if (stored.isPresent()) {
                try {
                    String baseUrl = urlValidator == null
                        ? stored.get().baseUrl()
                        : urlValidator.validate(stored.get().baseUrl()).toString();
                    return SandboxProvider.fromApiName(stored.get().provider())
                        .map(provider -> new SandboxRuntimeSettings(provider, baseUrl, stored.get().enabled()));
                } catch (ApiException ignored) {
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
            } catch (ApiException ignored) {
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
            } catch (ApiException ignored) {
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
