package com.feng.dsagent.learning.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.learning.support.MasteryEstimator;
import com.feng.dsagent.learning.support.MasteryEstimator.Estimate;
import com.feng.dsagent.learning.support.MasteryEstimator.Evidence;
import com.feng.dsagent.learning.mapper.LearningMapMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningMapService {

    /** A reviewed lesson file starts with its lesson number: 03-01-栈与队列-….md */
    private static final Pattern LESSON_NUMBER = Pattern.compile("(\\d{2}-\\d{2})");
    private static final String ROOT_ID = "root";
    private static final String ROOT_TITLE = "数据结构";
    private static final int ROOT_LEVEL = 0;
    private static final int CHAPTER_LEVEL = 1;
    private static final int LESSON_LEVEL = 2;
    /** What the learner produced fades in a month; what they watched, in six weeks. */
    private static final double PRODUCED_HALF_LIFE_DAYS = 30;
    private static final double WATCHED_HALF_LIFE_DAYS = 45;
    /** A repeat of the same request is evidence of trying again, not of learning: it counts for a fifth. */
    private static final double REPEAT_CREDIT = 0.2;

    private final LearningMapMapper learningMap;
    private final ObjectMapper mapper = new ObjectMapper();

    public LearningMapService(LearningMapMapper learningMap) {
        this.learningMap = learningMap;
    }

    /** One node of the map: the root, a chapter, or a reviewed lesson inside a chapter. */
    public record LearningMapNode(String id, String parentId, int level, String title, Double mastery,
                                  double confidence, String status, Components components, Signals signals) {
    }

    /** The raw activity behind one node, for explaining a colour. Zero means "nothing recorded". */
    public record Signals(int classroomSteps, int classroomAnswered, int classroomCorrect, int classroomHints,
                          int classroomSkips, int classroomAsks, int animations, int animationRepeats,
                          int chatSessions, int chatQuestions, int codeRuns, int codePassed, int codeFailed) {

        public static Signals empty() {
            return new Signals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** Each dimension 0-1, or null when that module recorded nothing here. Explains the score, never hides it. */
    public record Components(Double classroom, Double animation, Double chat, Double code) {
    }

    public record LearningMap(Instant generatedAt, List<LearningMapNode> nodes) {
    }

    /** Everything this learner has done, laid out as the map they will look at. */
    public LearningMap forUser(long userId) {
        Instant now = Instant.now();
        Map<String, Bucket> buckets = new HashMap<>();
        collectClassroom(userId, now, buckets, chunkToLesson(), stepToLesson(userId));
        java.util.List<String> lessons = lessonIndex();
        collectAnimation(userId, now, buckets, lessons);
        collectCode(userId, now, buckets, lessons);
        collectChat(userId, buckets, lessons);

        List<LearningMapNode> nodes = new ArrayList<>();
        Evidence totalEvidence = Evidence.none();
        Signals totalSignals = Signals.empty();
        for (LearningMapMapper.ChapterRow chapter : learningMap.selectChapters()) {
            Bucket bucket = buckets.getOrDefault(chapter.id(), new Bucket());
            nodes.add(node(chapter.id(), ROOT_ID, CHAPTER_LEVEL, chapter.title(), bucket, false));
            totalEvidence = add(totalEvidence, bucket.evidence());
            totalSignals = mergeSignals(totalSignals, bucket.signals());
            for (LessonRow lesson : lessonsOf(chapter.id())) {
                // A lesson has no signal of its own yet: the modules record a chapter. It is here so the map
                // has the shape the learner is taught in, and so per-lesson counting has somewhere to land.
                String lessonId = chapter.id() + ":" + lesson.key();
                nodes.add(node(lessonId, chapter.id(), LESSON_LEVEL, lesson.title(), buckets.getOrDefault(lessonId, new Bucket()), false));
            }
        }
        nodes.add(0, nodeWith(ROOT_ID, null, ROOT_LEVEL, ROOT_TITLE, totalEvidence, totalSignals, false));
        return new LearningMap(Instant.now(), List.copyOf(nodes));
    }

    private static LearningMapNode node(String id, String parentId, int level, String title, Bucket bucket, boolean lesson) {
        if (lesson) {
            return nodeWith(id, parentId, level, title, Evidence.none(), Signals.empty(), true);
        }
        return nodeWith(id, parentId, level, title, bucket.evidence(), bucket.signals(), false);
    }

    private static LearningMapNode nodeWith(String id, String parentId, int level, String title, Evidence evidence,
                                            Signals signals, boolean lesson) {
        Estimate estimate = MasteryEstimator.estimate(evidence);
        return new LearningMapNode(id, parentId, level, title, estimate.mastery(), estimate.confidence(),
            estimate.status(), new Components(estimate.classroom(), estimate.animation(), null, estimate.code()), signals);
    }

    // ---------------------------------------------------------------- collecting

    /** Everything one chapter accumulated, kept together so evidence and its explanation cannot drift apart. */
    private static final class Bucket {
        private double attempts;
        private double correct;
        private double hints;
        private double skips;
        private double steps;
        private double chat;
        private final Map<String, double[]> animationGroups = new LinkedHashMap<>();
        private final Map<String, double[]> codeGroups = new LinkedHashMap<>();
        private final Set<String> chatSeen = new HashSet<>();
        private final int[] raw = new int[13];

        public Evidence evidence() {
            double animationRequests = 0;
            for (double[] group : animationGroups.values()) {
                animationRequests += group[0];
            }
            double codeRuns = 0;
            double codePassed = 0;
            for (double[] group : codeGroups.values()) {
                codeRuns += group[0];
                codePassed += group[1];
            }
            return new Evidence(attempts, correct, hints, skips, steps,
                animationGroups.size(), animationRequests, chat, codeRuns, codePassed);
        }

        public Signals signals() {
            return new Signals(raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7], raw[8], raw[9], raw[10], raw[11], raw[12]);
        }
    }

    /**
     * The classroom: which step, which attempt, whether a hint came first. A third attempt is worth less than
     * a first, and an answer given after a hint says less about what the learner can do alone.
     */
    /**
     * The classroom, attributed twice: once to the chapter, and once to the lesson the step is teaching.
     *
     * <p>The lesson comes from the step the event happened on ({@code step_index}) and the textbook chunks that
     * step cites ({@code sourceChunkIds}), so a question answered on page 40 counts towards the lesson that page
     * belongs to. Without this the map can only colour whole chapters, and a learner cannot tell which part of a
     * chapter is weak.
     */
    private void collectClassroom(long userId, Instant now, Map<String, Bucket> buckets,
                                  Map<String, String> chunkToLesson, Map<String, Map<Integer, String>> sessionStepLessons) {
        for (LearningMapMapper.ClassroomEventRow row : learningMap.selectClassroomEvents(userId)) {
            String chapterId = row.chapterId();
            Bucket chapter = bucketFor(buckets, chapterId);
            if (chapter == null) continue;
            // The same event also belongs to the lesson its step teaches, when that can be worked out.
            Map<Integer, String> lessonsOfSession = sessionStepLessons.getOrDefault(row.sessionId(), Map.of());
            Integer stepIndex = row.stepIndex();
            String lessonKey = stepIndex == null ? null : lessonsOfSession.get(stepIndex);
            Bucket lesson = lessonKey == null ? null : bucketFor(buckets, chapterId + ":" + lessonKey);

            double recency = recency(row.createdAt(), now, PRODUCED_HALF_LIFE_DAYS);
            String action = row.action();
            Integer attempt = row.attemptNo();
            boolean hinted = Boolean.TRUE.equals(row.hintUsed());
            int attemptNo = attempt == null ? 1 : attempt;
            double weight = Math.pow(0.7, Math.max(0, attemptNo - 1)) * (hinted ? 0.7 : 1.0);
            boolean correct = "CORRECT".equals(row.answerStatus());
            for (Bucket target : lesson == null ? List.of(chapter) : List.of(chapter, lesson)) {
                switch (action == null ? "" : action) {
                    case "ANSWER" -> {
                        target.attempts += recency * weight;
                        target.raw[1]++;
                        if (correct) { target.correct += recency * weight; target.raw[2]++; }
                    }
                    case "HINT" -> { target.hints += recency; target.raw[3]++; }
                    case "SKIP" -> { target.skips += recency; target.raw[4]++; }
                    case "ASK" -> target.raw[5]++;
                    default -> { target.steps += recency; target.raw[0]++; }
                }
            }
        }
    }

    /** Which lesson each textbook chunk belongs to, from the lesson number its file name starts with. */
    private Map<String, String> chunkToLesson() {
        Map<String, String> lessons = new HashMap<>();
        for (LearningMapMapper.ChunkPathRow row : learningMap.selectLessonChunkPaths()) {
            String path = row.sourcePath();
            Matcher matcher = LESSON_NUMBER.matcher(path.substring(path.lastIndexOf('/') + 1));
            if (matcher.find()) lessons.put(row.id(), matcher.group(1));
        }
        return lessons;
    }

    /** For every session of this learner, which lesson each step teaches. */
    private Map<String, Map<Integer, String>> stepToLesson(long userId) {
        Map<String, Map<Integer, String>> bySession = new HashMap<>();
        Map<String, String> chunkToLesson = chunkToLesson();
        for (LearningMapMapper.SessionScriptRow row : learningMap.selectSessionScripts(userId)) {
            Map<Integer, String> perStep = new HashMap<>();
            try {
                JsonNode steps = mapper.readTree(row.scriptJson()).path("steps");
                for (int index = 0; index < steps.size(); index++) {
                    JsonNode cited = steps.get(index).path("sourceChunkIds");
                    if (!cited.isArray() || cited.isEmpty()) continue;
                    String lesson = chunkToLesson.get(cited.get(0).asText());
                    if (lesson != null) perStep.put(index, lesson);
                }
            } catch (Exception unreadable) {
                // A script that cannot be read simply leaves its events attributed to the chapter alone.
            }
            bySession.put(row.sessionId(), perStep);
        }
        return bySession;
    }

    /** The animation lab: repeats of the identical request are one act of learning, counted for a fifth. */
    private void collectAnimation(long userId, Instant now, Map<String, Bucket> buckets, java.util.List<String> lessons) {
        for (LearningMapMapper.AnimationRow row : learningMap.selectAnimations(userId)) {
            Bucket bucket = bucketFor(buckets, row.chapterId());
            if (bucket == null) continue;
            bucket.raw[6]++;
            double recency = recency(row.createdAt(), now, WATCHED_HALF_LIFE_DAYS);
            String payload = row.payloadJson();
            bucket.animationGroups.computeIfAbsent(payload == null ? "" : payload, key -> {
                bucket.raw[7]++;
                return new double[] { 0, 0 };
            });
            double[] group = bucket.animationGroups.get(payload == null ? "" : payload);
            group[0] += group[0] == 0 ? recency : recency * REPEAT_CREDIT;
            // The record names only a chapter; the lesson is inferred from what the animation is called.
            Bucket lessonBucket = lessonBucket(buckets, lessons, row.title() + " " + payload + " " + structureWords(payload));
            if (lessonBucket != null) {
                lessonBucket.raw[6]++;
                String animationKey = payload == null ? "" : payload;
                lessonBucket.animationGroups.computeIfAbsent(animationKey, ignored -> { lessonBucket.raw[7]++; return new double[] { 0, 0 }; });
                double[] lessonGroup = lessonBucket.animationGroups.get(animationKey);
                lessonGroup[0] += lessonGroup[0] == 0 ? recency : recency * REPEAT_CREDIT;
            }
        }
    }

    /** The editor: byte-identical code run again is the same attempt, not a new one. */
    private void collectCode(long userId, Instant now, Map<String, Bucket> buckets, java.util.List<String> lessons) {
        for (LearningMapMapper.CodeRunRow row : learningMap.selectCodeRuns(userId)) {
            Bucket bucket = bucketFor(buckets, row.chapterId());
            if (bucket == null) continue;
            bucket.raw[10]++;
            boolean passed = "success".equals(row.status());
            if (passed) bucket.raw[11]++; else bucket.raw[12]++;
            double recency = recency(row.createdAt(), now, PRODUCED_HALF_LIFE_DAYS);
            bucket.codeGroups.computeIfAbsent(digest(row.sourceCode()), key -> new double[] { 0, 0 });
            double[] group = bucket.codeGroups.get(digest(row.sourceCode()));
            double credit = group[0] == 0 ? recency : recency * REPEAT_CREDIT;
            group[0] += credit;
            if (passed) group[1] += credit;
            // The code itself often says which topic it is about; anything silent stays on the chapter.
            String sourceText = row.sourceCode();
            Bucket lessonBucket = lessonBucket(buckets, lessons, sourceText == null ? "" : sourceText.substring(0, Math.min(4000, sourceText.length())));
            if (lessonBucket != null) {
                lessonBucket.raw[10]++;
                if (passed) lessonBucket.raw[11]++; else lessonBucket.raw[12]++;
                String lessonCodeKey = digest(sourceText);
                lessonBucket.codeGroups.computeIfAbsent(lessonCodeKey, ignored -> new double[] { 0, 0 });
                double[] lessonGroup = lessonBucket.codeGroups.get(lessonCodeKey);
                double lessonCredit = lessonGroup[0] == 0 ? recency : recency * REPEAT_CREDIT;
                lessonGroup[0] += lessonCredit;
                if (passed) lessonGroup[1] += lessonCredit;
            }
        }
    }

    /** The dialogue: asking is participation, so it feeds confidence; identical questions asked twice count once. */
    private void collectChat(long userId, Map<String, Bucket> buckets, java.util.List<String> lessons) {
        for (LearningMapMapper.ChatMessageRow row : learningMap.selectChatMessages(userId)) {
            Bucket bucket = bucketFor(buckets, row.chapterId());
            if (bucket == null) continue;
            bucket.raw[8] = bucket.raw[8] == 0 ? 1 : bucket.raw[8];
            String text = row.content() == null ? "" : row.content().trim().toLowerCase();
            if (bucket.chatSeen.add(text)) {
                bucket.chat += 1;
                bucket.raw[9]++;
                // What was asked about is a decent clue to which lesson the learner was working on.
                Bucket lessonBucket = lessonBucket(buckets, lessons, text);
                if (lessonBucket != null) {
                    lessonBucket.raw[8] = lessonBucket.raw[8] == 0 ? 1 : lessonBucket.raw[8];
                    if (lessonBucket.chatSeen.add(text)) { lessonBucket.chat += 1; lessonBucket.raw[9]++; }
                }
            }
        }
    }

    private static Bucket bucketFor(Map<String, Bucket> buckets, String chapterId) {
        if (chapterId == null) return null;
        return buckets.computeIfAbsent(chapterId, key -> new Bucket());
    }

    /** How much a single event counts today: a half every half-life period since it happened. */
    private static double recency(Timestamp created, Instant now, double halfLifeDays) {
        if (created == null) return 1.0;
        double days = Duration.between(created.toInstant(), now).toMinutes() / 1440.0;
        return Math.pow(0.5, Math.max(0, days) / halfLifeDays);
    }

    private static String digest(String text) {
        if (text == null) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                out.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
            }
            return out.toString();
        } catch (Exception unavailable) {
            return text;
        }
    }

    // ---------------------------------------------------------------- layout, unchanged

    /**
     * The words a learner would recognise for an animation's structure.
     *
     * <p>An animation is recorded with an English structure identifier (binary_tree, linked_list, ...) while a
     * lesson title is written in Chinese, so the two never share a word and the animation could not be placed.
     * This maps the identifier to what the course calls the same thing. It is a translation, not a guess.
     */
    private static String structureWords(String payload) {
        if (payload == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"(?:structure|type)\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
        if (!matcher.find()) return "";
        String structure = matcher.group(1).toLowerCase();
        if (structure.contains("binary_tree") || structure.contains("bst")) return "二叉树 二叉排序树 遍历";
        if (structure.contains("avl")) return "平衡二叉排序树 旋转";
        if (structure.contains("thread")) return "线索二叉树 遍历";
        if (structure.contains("tree") || structure.contains("forest")) return "树 二叉树 遍历 森林";
        if (structure.contains("linked")) return "链表 线性表";
        if (structure.contains("stack")) return "栈 队列";
        if (structure.contains("queue")) return "队列 栈";
        if (structure.contains("graph")) return "图 遍历";
        if (structure.contains("hash")) return "哈希 散列";
        if (structure.contains("sort")) return "排序";
        if (structure.contains("array")) return "数组";
        if (structure.contains("string")) return "串";
        if (structure.contains("heap")) return "堆";
        return "";
    }

    /** The words a lesson title is made of, used to place a record that only names a chapter. */
    private static String keywordsOf(String title) {
        StringBuilder words = new StringBuilder();
        for (String part : title.replaceAll("（已核验[^）]*）", "").split("[\\-、，,与和的及 ]+")) {
            if (part.trim().length() >= 2) words.append(part.trim()).append(" ");
        }
        return words.toString().trim();
    }

    /** Every reviewed lesson of every chapter, as chapterId|lessonKey|keywords. */
    private java.util.List<String> lessonIndex() {
        java.util.List<String> index = new java.util.ArrayList<>();
        for (LearningMapMapper.ChapterRow chapter : learningMap.selectChapters()) {
            for (LessonRow lesson : lessonsOf(chapter.id())) {
                index.add(chapter.id() + "|" + lesson.key() + "|" + keywordsOf(lesson.title()));
            }
        }
        return index;
    }

    /** The bucket of the lesson a record most likely belongs to, or null when nothing matches well enough. */
    private static Bucket lessonBucket(java.util.Map<String, Bucket> buckets, java.util.List<String> index, String text) {
        if (text == null || text.isBlank()) return null;
        String best = null;
        int bestScore = 0;
        for (String entry : index) {
            int score = 0;
            for (String word : entry.split("\\|")[2].split(" ")) {
                if (!word.isEmpty() && text.contains(word)) score += word.length() * word.length();
            }
            if (score > bestScore) { bestScore = score; best = entry; }
        }
        // Two matching characters is the floor: below that a match is coincidence, not meaning.
        if (best == null || bestScore < 4) return null;
        String[] parts = best.split("\\|");
        return bucketFor(buckets, parts[0] + ":" + parts[1]);
    }

    private record LessonRow(String key, String title) {
    }

    /** The reviewed lessons of a chapter, keyed by the lesson number their file name starts with. */
    private List<LessonRow> lessonsOf(String chapterId) {
        Map<String, LessonRow> lessons = new LinkedHashMap<>();
        for (LearningMapMapper.LessonChunkRow row : learningMap.selectLessonChunksByChapter(chapterId)) {
            String path = row.sourcePath();
            Matcher matcher = LESSON_NUMBER.matcher(path.substring(path.lastIndexOf('/') + 1));
            if (matcher.find()) {
                lessons.putIfAbsent(matcher.group(1), new LessonRow(matcher.group(1), row.title()));
            }
        }
        return List.copyOf(lessons.values());
    }

    private static Evidence add(Evidence left, Evidence right) {
        return new Evidence(
            left.classroomAttempts() + right.classroomAttempts(),
            left.classroomCorrect() + right.classroomCorrect(),
            left.classroomHints() + right.classroomHints(),
            left.classroomSkips() + right.classroomSkips(),
            left.classroomSteps() + right.classroomSteps(),
            left.animationUnique() + right.animationUnique(),
            left.animationRequests() + right.animationRequests(),
            left.chatQuestions() + right.chatQuestions(),
            left.codeRuns() + right.codeRuns(),
            left.codePassed() + right.codePassed());
    }

    private static Signals mergeSignals(Signals left, Signals right) {
        return new Signals(
            left.classroomSteps() + right.classroomSteps(),
            left.classroomAnswered() + right.classroomAnswered(),
            left.classroomCorrect() + right.classroomCorrect(),
            left.classroomHints() + right.classroomHints(),
            left.classroomSkips() + right.classroomSkips(),
            left.classroomAsks() + right.classroomAsks(),
            left.animations() + right.animations(),
            left.animationRepeats() + right.animationRepeats(),
            left.chatSessions() + right.chatSessions(),
            left.chatQuestions() + right.chatQuestions(),
            left.codeRuns() + right.codeRuns(),
            left.codePassed() + right.codePassed(),
            left.codeFailed() + right.codeFailed());
    }
}
