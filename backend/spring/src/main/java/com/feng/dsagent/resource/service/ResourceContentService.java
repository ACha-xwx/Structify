package com.feng.dsagent.resource.service;

import com.feng.dsagent.resource.config.ResourceProperties;
import com.feng.dsagent.resource.converter.ResourceConverter;
import com.feng.dsagent.resource.mapper.ResourceMapper;
import com.feng.dsagent.resource.domain.ResourceAudience;
import com.feng.dsagent.resource.domain.ResourceContent;
import com.feng.dsagent.resource.support.ResourceAsset;

import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.resource.error.ResourceErrorCode;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class ResourceContentService {
    private final ResourceMapper resources;
    private final ResourceConverter resourceConverter;
    private final ResourceProperties properties;

    public ResourceContentService(
        ResourceMapper resources,
        ResourceConverter resourceConverter,
        ResourceProperties properties
    ) {
        this.resources = resources;
        this.resourceConverter = resourceConverter;
        this.properties = properties;
    }

    public ResourceContent content(String id, AuthenticatedUser user) {
        ResourceAudience audience = ResourceAudience.from(user);
        ResourceMapper.ResourceRow row = resources.selectPublishedById(
            id,
            audience.allowClassroomOnly(),
            audience.allowTeamOnly()
        );
        if (row == null) {
            throw ResourceErrorCode.NOT_FOUND.toException();
        }
        ResourceAsset asset = resourceConverter.toAsset(row);
        Path file = resolve(asset.filePath());
        return new ResourceContent(
            new FileSystemResource(file),
            mediaType(file),
            file.getFileName().toString(),
            inline(file)
        );
    }

    private Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw contentNotFound();
        }
        try {
            Path root = properties.root();
            if (!Files.isDirectory(root)) {
                throw contentNotFound();
            }
            Path configured = Path.of(storedPath.trim());
            if (configured.isAbsolute()) {
                throw contentNotFound();
            }
            Path candidate = root.resolve(configured).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
                throw contentNotFound();
            }
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw contentNotFound();
            }
            return realCandidate;
        } catch (BizException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw contentNotFound();
        }
    }

    private MediaType mediaType(Path file) {
        try {
            String detected = Files.probeContentType(file);
            if (detected != null && !detected.isBlank()) {
                return MediaType.parseMediaType(detected);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Use a deterministic extension fallback when the platform does not know the file type.
        }
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".c") || name.endsWith(".py")) {
            return MediaType.TEXT_PLAIN;
        }
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean inline(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".md") || name.endsWith(".txt")
            || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private BizException contentNotFound() {
        return ResourceErrorCode.CONTENT_NOT_FOUND.toException();
    }
}
