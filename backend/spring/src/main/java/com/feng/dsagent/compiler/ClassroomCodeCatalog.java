package com.feng.dsagent.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only catalog of the classroom code samples that ship with the application. The file is a
 * build resource, so a new sample only ever reaches a deployment through the normal release
 * pipeline - never by editing a running server.
 */
@Component
final class ClassroomCodeCatalog {

    private static final String RESOURCE = "classroom-code/lessons.json";

    private final List<ClassroomCodeLesson> lessons;

    @Autowired
    ClassroomCodeCatalog(ObjectMapper objectMapper) {
        this.lessons = load(objectMapper);
    }

    ClassroomCodeCatalog(List<ClassroomCodeLesson> lessons) {
        this.lessons = List.copyOf(lessons);
    }

    List<ClassroomCodeLesson> lessons() {
        return lessons;
    }

    Optional<ClassroomCodeLesson> lesson(String coursewareKey) {
        if (coursewareKey == null || coursewareKey.isBlank()) {
            return Optional.empty();
        }
        String wanted = coursewareKey.strip();
        return lessons.stream().filter(lesson -> wanted.equals(lesson.coursewareKey())).findFirst();
    }

    int sampleCount() {
        return lessons.stream().mapToInt(lesson -> lesson.samples().size()).sum();
    }

    private static List<ClassroomCodeLesson> load(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream stream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            JsonNode node = root.path("lessons");
            if (!node.isArray()) {
                return List.of();
            }
            List<ClassroomCodeLesson> parsed = new ArrayList<>();
            for (JsonNode lessonNode : node) {
                List<ClassroomCodeSample> samples = new ArrayList<>();
                for (JsonNode sampleNode : lessonNode.path("samples")) {
                    samples.add(new ClassroomCodeSample(
                        text(sampleNode, "id"),
                        text(sampleNode, "title"),
                        text(sampleNode, "file"),
                        strings(sampleNode.path("sections")),
                        strings(sampleNode.path("targets")),
                        text(sampleNode, "summary"),
                        text(sampleNode, "stdin"),
                        text(sampleNode, "expectedStdout"),
                        text(sampleNode, "code")
                    ));
                }
                parsed.add(new ClassroomCodeLesson(
                    text(lessonNode, "coursewareKey"),
                    text(lessonNode, "lessonTitle"),
                    text(lessonNode, "chapterId"),
                    samples
                ));
            }
            return List.copyOf(parsed);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read " + RESOURCE, error);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }
}
