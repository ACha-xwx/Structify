package com.feng.dsagent.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only catalog of the class listings that ship inside the app. Every fragment is the source as
 * printed, kept verbatim including its comments; none of them carries a {@code main}, which is why
 * each one travels with a complete runnable example instead.
 */
@Component
final class TextbookCodeLibrary {

    private static final String RESOURCE = "classroom-code/textbook/library.json";

    private final List<TextbookCodeChapter> chapters;
    private final int fragmentCount;

    @Autowired
    TextbookCodeLibrary(ObjectMapper objectMapper) {
        JsonNode root = read(objectMapper);
        this.chapters = parseChapters(root);
        this.fragmentCount = chapters.stream().mapToInt(chapter -> chapter.fragments().size()).sum();
    }

    List<TextbookCodeChapter> chapters() {
        return chapters;
    }

    int fragmentCount() {
        return fragmentCount;
    }

    private static JsonNode read(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            return objectMapper.createObjectNode();
        }
        try (InputStream stream = resource.getInputStream()) {
            return objectMapper.readTree(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read " + RESOURCE, error);
        }
    }

    private static List<TextbookCodeChapter> parseChapters(JsonNode root) {
        JsonNode node = root.path("chapters");
        if (!node.isArray()) {
            return List.of();
        }
        List<TextbookCodeChapter> parsed = new ArrayList<>();
        for (JsonNode chapterNode : node) {
            List<TextbookCodeFragment> fragments = new ArrayList<>();
            for (JsonNode fragmentNode : chapterNode.path("fragments")) {
                fragments.add(new TextbookCodeFragment(
                    text(fragmentNode, "id"),
                    text(fragmentNode, "file"),
                    text(fragmentNode, "title"),
                    text(fragmentNode, "kind"),
                    text(fragmentNode, "startWith"),
                    text(fragmentNode, "code"),
                    example(fragmentNode.path("example")),
                    text(fragmentNode, "exampleBlocked")
                ));
            }
            parsed.add(new TextbookCodeChapter(
                text(chapterNode, "chapter"),
                text(chapterNode, "title"),
                fragments
            ));
        }
        return List.copyOf(parsed);
    }

    private static TextbookCodeExample example(JsonNode node) {
        if (!node.isObject() || text(node, "code").isBlank()) {
            return null;
        }
        return new TextbookCodeExample(
            text(node, "code"),
            text(node, "stdin"),
            text(node, "expectedStdout"),
            text(node, "note")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}
