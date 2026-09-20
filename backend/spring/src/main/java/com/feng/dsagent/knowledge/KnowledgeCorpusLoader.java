package com.feng.dsagent.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads locally reviewed textbook lessons into classroom knowledge chunks.
 *
 * <p>Publication is gated by a machine readable page-level manifest: a lesson file contributes
 * chunks only when it is listed in {@code import-manifest.json}, its bytes match the recorded
 * SHA-256, every page block carries the reviewed label, and the declared textbook/PDF pages match
 * the manifest exactly. Anything else is skipped and reported instead of being published.
 */
public final class KnowledgeCorpusLoader {

    private static final Pattern LESSON_FILE = Pattern.compile("^(\\d{2})-\\d{2}-.+\\.md$");
    private static final Pattern PAGE_HEADER = Pattern.compile("^### 教材页 (\\d+)（PDF页 (\\d+)）$");
    private static final Pattern TITLE = Pattern.compile("(?m)^#\\s*课时标题[：:]\\s*(.+?)\\s*$");
    private static final Map<String, String> CHAPTER_IDS = Map.ofEntries(
        Map.entry("01", "01-introduction"),
        Map.entry("02", "02-linear-list"),
        Map.entry("03", "03-stack-queue"),
        Map.entry("04", "04-string"),
        Map.entry("05", "05-array-generalized-list"),
        Map.entry("06", "06-tree"),
        Map.entry("07", "07-graph"),
        Map.entry("08", "08-search"),
        Map.entry("09", "09-internal-sort"),
        Map.entry("10", "10-external-sort")
    );

    private final ObjectMapper mapper;

    public KnowledgeCorpusLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public KnowledgeCorpus load(Path textbookDirectory, int chunkSize) {
        if (textbookDirectory == null) {
            return KnowledgeCorpus.empty();
        }
        Path root = textbookDirectory.toAbsolutePath().normalize();
        Path lessonsDirectory = root.resolve("lessons");
        if (!Files.isDirectory(lessonsDirectory)) {
            return KnowledgeCorpus.empty();
        }

        KnowledgeImportManifest manifest;
        try {
            manifest = KnowledgeImportManifest.read(root, mapper);
        } catch (RuntimeException error) {
            return KnowledgeCorpus.empty(List.of(new KnowledgeImportManifest.Rejection(
                KnowledgeImportManifest.FILE_NAME,
                "IMPORT_MANIFEST_UNUSABLE: " + error.getClass().getSimpleName() + ": " + error.getMessage()
            )));
        }
        if (manifest == null) {
            return KnowledgeCorpus.empty(List.of(new KnowledgeImportManifest.Rejection(
                KnowledgeImportManifest.FILE_NAME, "IMPORT_MANIFEST_MISSING"
            )));
        }

        List<Path> lessonFiles;
        try (Stream<Path> files = Files.list(lessonsDirectory)) {
            lessonFiles = files
                .filter(Files::isRegularFile)
                .filter(path -> LESSON_FILE.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to list textbook lessons", error);
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<KnowledgeImportManifest.Rejection> rejections = new ArrayList<>();
        int acceptedLessons = 0;
        for (Path lessonFile : lessonFiles) {
            String filename = lessonFile.getFileName().toString();
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(lessonFile);
            } catch (IOException error) {
                throw new IllegalStateException("Unable to read textbook lesson " + filename, error);
            }
            String markdown = new String(bytes, StandardCharsets.UTF_8);
            List<KnowledgeImportManifest.LessonPageBlock> blocks = pageBlocks(markdown);
            KnowledgeImportManifest.Entry entry = manifest.accepted().get(filename);
            List<KnowledgeImportManifest.Rejection> problems =
                KnowledgeImportManifest.validate(filename, sha256(bytes), blocks, entry);
            if (!problems.isEmpty()) {
                rejections.addAll(problems);
                continue;
            }
            String chapterId = chapterId(filename);
            if (chapterId == null) {
                rejections.add(new KnowledgeImportManifest.Rejection(filename, "UNKNOWN_CHAPTER_PREFIX"));
                continue;
            }
            String title = match(TITLE, markdown, filename.substring(0, filename.length() - 3));
            String source = "textbook/lessons/" + filename;
            // One chunk per reviewed page, split further only when a page exceeds the budget. Page-scoped
            // chunks are what let a classroom step cite the exact page it drew from and what lets a
            // courseware page's section number select the textbook passages that belong to it.
            for (KnowledgeImportManifest.LessonPageBlock block : blocks) {
                List<String> parts = split(block.text(), Math.max(80, chunkSize));
                for (int part = 0; part < parts.size(); part++) {
                    chunks.add(new KnowledgeChunk(
                        chunkId(source, block.page(), part),
                        chapterId,
                        title,
                        parts.get(part),
                        source,
                        "第 " + block.page() + " 页",
                        "CLASSROOM_ONLY"
                    ));
                }
            }
            acceptedLessons++;
        }
        return new KnowledgeCorpus(
            chunks,
            new KnowledgeCorpusStats(acceptedLessons > 0, acceptedLessons, chunks.size(), List.copyOf(rejections))
        );
    }

    /** Recovers the exact text of every {@code ### 教材页 N（PDF页 M）} block, in file order. */
    static List<KnowledgeImportManifest.LessonPageBlock> pageBlocks(String markdown) {
        List<String> lines = List.of(markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        List<KnowledgeImportManifest.LessonPageBlock> blocks = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Matcher header = PAGE_HEADER.matcher(lines.get(index).trim());
            if (!header.matches()) {
                continue;
            }
            int end = index + 1;
            while (end < lines.size()
                && !PAGE_HEADER.matcher(lines.get(end).trim()).matches()
                && !lines.get(end).startsWith("## ")) {
                end++;
            }
            List<String> block = lines.subList(index, end);
            String label = block.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("> OCR质量："))
                .findFirst()
                .orElse("");
            blocks.add(new KnowledgeImportManifest.LessonPageBlock(
                Integer.parseInt(header.group(1)),
                Integer.parseInt(header.group(2)),
                label,
                String.join("\n", block).strip()
            ));
            index = end - 1;
        }
        return blocks;
    }

    private List<String> split(String markdown, int chunkSize) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : normalized.split("\\n\\s*\\n")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + trimmed.length() > chunkSize) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (trimmed.length() > chunkSize) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                splitLongBlock(trimmed, chunkSize, result);
                continue;
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private void splitLongBlock(String block, int chunkSize, List<String> output) {
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + chunkSize);
            if (end < block.length()) {
                int lineBreak = block.lastIndexOf('\n', end);
                int sentenceBreak = Math.max(block.lastIndexOf('。', end), block.lastIndexOf('；', end));
                int preferred = Math.max(lineBreak, sentenceBreak);
                if (preferred > start + chunkSize / 2) {
                    end = preferred + 1;
                }
            }
            output.add(block.substring(start, end).trim());
            start = end;
        }
    }

    private String chapterId(String filename) {
        Matcher matcher = LESSON_FILE.matcher(filename);
        return matcher.matches() ? CHAPTER_IDS.get(matcher.group(1)) : null;
    }

    private String match(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private String chunkId(String source, int page, int part) {
        return "textbook-" + sha256((source + "#" + page + "#" + part).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    /** Exposed for diagnostics and tests; keeps the chapter prefix mapping in one place. */
    static Set<String> knownChapterIds() {
        return new LinkedHashSet<>(CHAPTER_IDS.values());
    }
}
