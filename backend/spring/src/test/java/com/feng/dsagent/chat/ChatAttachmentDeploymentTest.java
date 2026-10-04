package com.feng.dsagent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

/** Exercises the storage configuration used by the non-root production container. */
class ChatAttachmentDeploymentTest {

    @ParameterizedTest
    @ValueSource(strings = {"docker-compose.production.yml", "docker-compose.spring.yml"})
    @SuppressWarnings("unchecked")
    void attachmentDirectoryLivesOnAPrivateWritablePersistentVolume(String composeFile) throws Exception {
        Path repo = Path.of("../..").toAbsolutePath().normalize();
        Map<String, Object> compose = new Yaml().load(Files.readString(repo.resolve("deployment/" + composeFile)));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> spring = (Map<String, Object>) services.get("spring-api");
        Map<String, Object> environment = (Map<String, Object>) spring.get("environment");
        String directory = (String) environment.get("CHAT_ATTACHMENTS_DIR");
        assertThat(directory)
            .as("Attachments must not fall back to ../../private on the container's read-only root filesystem")
            .isNotBlank().startsWith("/app/");

        List<String> mounts = (List<String>) spring.get("volumes");
        String mount = mounts.stream()
            .filter(value -> value.split(":").length >= 2 && directory.equals(value.split(":")[1]))
            .findFirst().orElseThrow(() -> new AssertionError("No persistent mount for " + directory));
        String[] parts = mount.split(":");
        assertThat(parts.length == 2 || "rw".equals(parts[2])).as("Attachment mount must be writable").isTrue();
        Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");
        assertThat(volumes).containsKey(parts[0]);
        for (Map.Entry<String, Object> service : services.entrySet()) {
            if ("spring-api".equals(service.getKey())) continue;
            Map<String, Object> other = (Map<String, Object>) service.getValue();
            List<String> otherMounts = (List<String>) other.getOrDefault("volumes", List.of());
            assertThat(otherMounts).noneMatch(value -> value.startsWith(parts[0] + ":"));
        }

        String dockerfile = Files.readString(repo.resolve("backend/spring/Dockerfile"));
        assertThat(dockerfile).contains("USER appuser");
        assertThat(dockerfile).contains("install -d -m 700 -o appuser -g appuser " + directory);
        if ("docker-compose.production.yml".equals(composeFile)) {
            assertThat(spring.get("read_only")).isEqualTo(true);
        }
    }
}
