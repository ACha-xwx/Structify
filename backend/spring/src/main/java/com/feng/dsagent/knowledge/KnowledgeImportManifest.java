package com.feng.dsagent.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Page-level allow list for locally reviewed textbook material.
 *
 * <p>The loader refuses to publish anything that is not listed here, so an unreviewed OCR file
 * cannot become classroom material just because it was dropped into the knowledge directory.
 * The manifest lives outside the tracked tree together with the reviewed textbook itself.
 */
record KnowledgeImportManifest(Map<String, Entry> accepted) {

    static final String FILE_NAME = "import-manifest.json";
    static final int SUPPORTED_VERSION = 2;

    /** Only pages carrying this exact label prefix may be published. */
    static final String REVIEWED_LABEL_PREFIX = "> OCR质量：已对照原始教材 PDF 核验（";

    record Entry(String source, String targetSha256, String pageRange, Map<Integer, Integer> pdfPages) {
        Entry {
            pdfPages = Map.copyOf(pdfPages);
        }

        int pageCount() {
            return pdfPages.size();
        }
    }

    record Rejection(String fileName, String reason) {
    }

    /** Reads the manifest, or returns {@code null} when the knowledge directory has no manifest. */
    static KnowledgeImportManifest read(Path textbookDirectory, ObjectMapper mapper) {
        Path manifestFile = textbookDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(manifestFile)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(manifestFile);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read the textbook import manifest", error);
        }
        JsonNode root = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        int version = root.path("version").asInt(0);
        if (version < SUPPORTED_VERSION) {
            throw new IllegalStateException(
                "Textbook import manifest version " + version + " is not supported; expected " + SUPPORTED_VERSION
            );
        }
        JsonNode accepted = root.path("accepted");
        if (!accepted.isArray()) {
            throw new IllegalStateException("Textbook import manifest must contain an accepted array");
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (JsonNode group : accepted) {
            String source = group.path("source").asText("").trim();
            String target = group.path("target").asText("").trim();
            String targetSha256 = group.path("targetSha256").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            String pageRange = group.path("pageRange").asText("").trim();
            String fileName = fileNameOf(target);
            if (source.isEmpty() || fileName.isEmpty()) {
                throw new IllegalStateException("Textbook import manifest entry is missing source or target");
            }
            Map<Integer, Integer> pdfPages = new LinkedHashMap<>();
            JsonNode pages = group.path("pages");
            if (!pages.isArray() || pages.isEmpty()) {
                throw new IllegalStateException("Textbook import manifest entry has no reviewed pages: " + fileName);
            }
            for (JsonNode page : pages) {
                int textbookPage = page.path("page").asInt(0);
                int pdfPage = page.path("pdfPage").asInt(0);
                if (textbookPage <= 0 || pdfPage <= 0) {
                    throw new IllegalStateException("Textbook import manifest has an invalid page for " + fileName);
                }
                if (pdfPages.put(textbookPage, pdfPage) != null) {
                    throw new IllegalStateException("Textbook import manifest repeats page " + textbookPage + " for " + fileName);
                }
            }
            Entry entry = new Entry(source, targetSha256, pageRange, pdfPages);
            if (entries.put(fileName, entry) != null) {
                throw new IllegalStateException("Textbook import manifest lists " + fileName + " more than once");
            }
        }
        return new KnowledgeImportManifest(entries);
    }

    private static String fileNameOf(String target) {
        String normalized = target.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    /** Returns the reasons why a candidate lesson file must stay unpublished. */
    static List<Rejection> validate(String fileName, String sha256, List<LessonPageBlock> blocks, Entry entry) {
        if (entry == null) {
            return List.of(new Rejection(fileName, "NOT_LISTED_IN_IMPORT_MANIFEST"));
        }
        java.util.ArrayList<Rejection> rejections = new java.util.ArrayList<>();
        if (entry.targetSha256().length() != 64 || !entry.targetSha256().matches("[0-9a-f]{64}")) {
            rejections.add(new Rejection(fileName, "MANIFEST_TARGET_HASH_INVALID"));
        } else if (!entry.targetSha256().equals(sha256)) {
            rejections.add(new Rejection(fileName, "TARGET_HASH_MISMATCH"));
        }
        if (entry.pageRange().isBlank()) {
            rejections.add(new Rejection(fileName, "MANIFEST_PAGE_RANGE_MISSING"));
        }
        if (blocks.isEmpty()) {
            rejections.add(new Rejection(fileName, "NO_PAGE_BLOCKS"));
        }
        java.util.Set<Integer> declared = new java.util.LinkedHashSet<>();
        for (LessonPageBlock block : blocks) {
            declared.add(block.page());
            if (!block.label().startsWith(REVIEWED_LABEL_PREFIX)) {
                rejections.add(new Rejection(fileName, "PAGE_" + block.page() + "_LABEL_NOT_REVIEWED"));
            }
            Integer expectedPdfPage = entry.pdfPages().get(block.page());
            if (expectedPdfPage == null) {
                rejections.add(new Rejection(fileName, "PAGE_" + block.page() + "_NOT_LISTED_IN_IMPORT_MANIFEST"));
            } else if (expectedPdfPage != block.pdfPage()) {
                rejections.add(new Rejection(fileName, "PAGE_" + block.page() + "_PDF_PAGE_MISMATCH"));
            }
        }
        if (!rejections.isEmpty()) {
            return List.copyOf(rejections);
        }
        if (!declared.equals(new java.util.LinkedHashSet<>(entry.pdfPages().keySet()))) {
            rejections.add(new Rejection(fileName, "PAGE_SET_MISMATCH"));
        }
        return List.copyOf(rejections);
    }

    /** A single {@code ### 教材页 N（PDF页 M）} block recovered from a textbook lesson file. */
    record LessonPageBlock(int page, int pdfPage, String label, String text) {
    }
}
