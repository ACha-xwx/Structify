package com.feng.dsagent.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Imports the user's original PPT extraction, keeping PPT page numbers distinct from textbook pages. */
final class CoursewareKnowledgeLoader {
    private static final Pattern PAGE = Pattern.compile("(?m)^## 第 (\\d+) 页：([^\\n]*)\\n");
    private static final Map<String, String> CHAPTERS = Map.of(
        "第一章绪论", "01-introduction", "第二章线性表", "02-linear-list",
        "第三章限定性线性表", "03-stack-queue", "第五章数组和广义表", "05-array-generalized-list",
        "第六章树和二叉树", "06-tree", "第七章图", "07-graph",
        "第八章查找", "08-search", "第九章排序", "09-internal-sort");
    private final ObjectMapper mapper;
    CoursewareKnowledgeLoader(ObjectMapper mapper) { this.mapper = mapper; }

    KnowledgeCorpus load(Path root, int chunkSize) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<KnowledgeImportManifest.Rejection> rejected = new ArrayList<>();
        int accepted = 0;
        try {
            JsonNode manifest = mapper.readTree(Files.readString(root.resolve("转换清单.json")));
            List<JsonNode> decks = new ArrayList<>();
            manifest.path("decks").forEach(decks::add);
            decks.sort(Comparator.comparing(node -> node.path("source").asText()));
            Map<String, Integer> numbers = new HashMap<>();
            for (JsonNode deck : decks) {
                String original = deck.path("markdown").asText();
                String chapter = CHAPTERS.get(deck.path("chapter").asText());
                if (chapter == null) throw new IllegalArgumentException("Unknown chapter: " + original);
                int number = numbers.merge(chapter, 1, Integer::sum);
                String relative = original.replaceFirst("^知识库/", "");
                Path file = root.resolve(relative).normalize();
                if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath())) {
                    throw new IllegalArgumentException("Courseware source escapes root");
                }
                String markdown = Files.readString(file).replace("\r\n", "\n");
                var matcher = PAGE.matcher(markdown);
                List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>(), pages = new ArrayList<>();
                List<String> titles = new ArrayList<>();
                while (matcher.find()) {
                    starts.add(matcher.start()); ends.add(matcher.end());
                    pages.add(Integer.parseInt(matcher.group(1))); titles.add(matcher.group(2).trim());
                }
                int count = deck.path("slides").asInt();
                boolean complete = count > 0 && pages.size() == count;
                for (int i = 0; i < pages.size(); i++) complete &= pages.get(i) == i + 1;
                if (!complete) {
                    rejected.add(new KnowledgeImportManifest.Rejection(original, "PPT_PAGE_SET_MISMATCH"));
                    continue;
                }
                String deckTitle = file.getFileName().toString().replaceFirst("\\.md$", "");
                String source = "courseware/lessons/" + chapter.substring(0, 2) + "-" + String.format(Locale.ROOT, "%02d", number) + "-" + deckTitle + ".md";
                for (int i = 0; i < pages.size(); i++) {
                    String content = markdown.substring(ends.get(i), i + 1 < pages.size() ? starts.get(i + 1) : markdown.length())
                        .replaceAll("(?m)^<a id=\"slide-[^\\n]*\\n?", "")
                        .replaceAll("(?m)^!?\\[(?:查看 WebP 原图|第 \\d+ 页)\\]\\([^\\n]*\\)\\s*$", "").trim();
                    if (content.isBlank()) continue;
                    // Preserve a page as one evidence unit. Large pages are bounded without mixing slide provenance.
                    int budget = Math.max(80, chunkSize);
                    for (int offset = 0, part = 0; offset < content.length(); offset += budget, part++) {
                        String id = "courseware-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest((source + ":" + pages.get(i) + ":" + part).getBytes(StandardCharsets.UTF_8))).substring(0, 28);
                        chunks.add(new KnowledgeChunk(id, chapter, deckTitle + " · " + titles.get(i),
                            content.substring(offset, Math.min(content.length(), offset + budget)), source,
                            "PPT第 " + pages.get(i) + " 页", "CLASSROOM_ONLY"));
                    }
                }
                accepted++;
            }
        } catch (Exception error) {
            return KnowledgeCorpus.empty(List.of(new KnowledgeImportManifest.Rejection("转换清单.json", error.getClass().getSimpleName() + ": " + error.getMessage())));
        }
        // An incomplete release cannot silently replace a working corpus.
        if (!rejected.isEmpty()) return KnowledgeCorpus.empty(rejected);
        return new KnowledgeCorpus(chunks, new KnowledgeCorpusStats(!chunks.isEmpty(), accepted, chunks.size(), rejected));
    }
}
