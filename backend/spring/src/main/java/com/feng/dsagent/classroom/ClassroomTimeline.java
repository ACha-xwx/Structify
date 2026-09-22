package com.feng.dsagent.classroom;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.learning.LearningEventCommand;
import com.feng.dsagent.learning.LearningEventService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** A durable cursor over the complete lesson; responses never silently advance it. */
@Service
public class ClassroomTimeline {
    private final ClassroomRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClassroomScriptParser parser;
    private final ClassroomModelJson model;
    private final LearningEventService events;
    private final com.feng.dsagent.animation.DsvpAnimationAdapter animations;
    private final ClassroomSlideAlignment slides;
    public ClassroomTimeline(ClassroomRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
            ClassroomScriptParser parser, ClassroomModelJson model, LearningEventService events,
            com.feng.dsagent.animation.DsvpAnimationAdapter animations, ClassroomSlideAlignment slides) {
        this.repository = repository; this.jdbc = jdbc; this.mapper = mapper; this.parser = parser; this.model = model; this.events = events;
        this.animations = animations;
        this.slides = slides;
    }
    private record Cursor(int index, int revision, String response) {}
    private Cursor cursor(String id) {
        return jdbc.queryForObject("SELECT runtime_index, runtime_revision, runtime_response FROM classroom_sessions WHERE id = ?", (row, n) -> new Cursor(row.getInt(1), row.getInt(2), row.getString(3)), id);
    }
    private ClassroomSessionRecord owned(long userId, String id) {
        return repository.findSession(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASSROOM_SESSION_NOT_FOUND", "课堂会话不存在"));
    }
    private List<JsonNode> steps(ClassroomSessionRecord session) {
        parser.parse(session.scriptJson());
        JsonNode root = mapper.readTree(session.scriptJson());
        List<JsonNode> result = new ArrayList<>();
        if (root.path("steps").isArray()) root.path("steps").forEach(step -> result.add(step.deepCopy()));
        else {
            for (ClassroomState state : List.of(ClassroomState.EXPLAIN, ClassroomState.WAITING, ClassroomState.DISCUSS, ClassroomState.BLACKBOARD, ClassroomState.SUMMARY)) {
                JsonNode source = root.path("stages").path(state.name());
                if (!source.isObject() || source.isEmpty()) continue;
                ObjectNode stage = (ObjectNode) source.deepCopy();
                stage.put("type", state == ClassroomState.WAITING ? "question" : state == ClassroomState.DISCUSS ? "discussion" : state.name().toLowerCase(Locale.ROOT));
                result.add(stage);
            }
        }
        return result;
    }
    private ClassroomState state(JsonNode step) {
        return switch (step.path("type").asText()) {
            case "question" -> ClassroomState.WAITING;
            case "discussion" -> ClassroomState.DISCUSS;
            case "blackboard" -> ClassroomState.BLACKBOARD;
            case "summary" -> ClassroomState.SUMMARY;
            default -> ClassroomState.EXPLAIN;
        };
    }
    @Transactional
    public ClassroomSessionView get(long userId, String id) { return view(owned(userId, id)); }
    public ClassroomSessionView view(ClassroomSessionRecord session) {
        List<JsonNode> steps = steps(session); Cursor cursor = cursor(session.id());
        ObjectNode stage;
        if (cursor.index() < 0) stage = (ObjectNode) parser.parse(session.scriptJson()).stage(ClassroomState.OPENING).deepCopy();
        else if (cursor.index() < steps.size()) stage = (ObjectNode) steps.get(cursor.index()).deepCopy();
        else { stage = mapper.createObjectNode(); stage.put("content", "本课已结束。"); }
        // Answer keys are retained server-side only.
        stage.remove(List.of("expected", "misconceptions", "misconceptionFeedback", "teacherResponse", "answerEvaluation"));
        stage.put("stepIndex", cursor.index()); stage.put("stepCount", steps.size()); stage.put("revision", cursor.revision()); stage.put("chapterId", session.chapterId());
        // The slide pane needs the lesson identity to load the courseware prepared for this lesson.
        String lessonId = mapper.readTree(session.scriptJson()).path("lessonId").asText("");
        stage.put("lessonId", lessonId);
        // Which courseware page belongs to this step: a human correction wins, then the model's own
        // slideRefs, then the deterministic alignment against the lesson deck. A step with no page of its
        // own keeps the current page and reports why, so the pane never jumps to an unrelated page.
        JsonNode activeStep = cursor.index() >= 0 && cursor.index() < steps.size() ? steps.get(cursor.index()) : null;
        ClassroomSlideAlignment.StepSlide slide = slides.forStep(session.scriptId(), session.scriptJson(), lessonId, cursor.index(), activeStep);
        if (slide != null) {
            var references = mapper.createArrayNode();
            references.add(slide.slideId());
            stage.set("slideRefs", references);
            stage.set("slideMatch", slides.describe(slide));
        }
        ClassroomAnswerEvaluation evaluation = null;
        if (cursor.response() != null) {
            JsonNode response = mapper.readTree(cursor.response());
            stage.set("teacherResponse", response);
            if (response.path("animationRef").isObject()) stage.set("animationRef", response.path("animationRef"));
            if (response.has("status")) evaluation = new ClassroomAnswerEvaluation(ClassroomAnswerStatus.valueOf(response.path("status").asText()), response.path("misconception").isTextual() ? response.path("misconception").asText() : null, response.path("feedback").asText());
        }
        return new ClassroomSessionView(session.id(), session.userId(), session.scriptId(), session.state(), session.paused(), session.summary(), stage, evaluation);
    }
    @Transactional
    public ClassroomSessionView apply(long userId, String id, ClassroomAction action, String content, Integer expectedRevision) {
        ClassroomSessionRecord session = owned(userId, id); Cursor current = cursor(id); List<JsonNode> steps = steps(session);
        if (expectedRevision != null && expectedRevision != current.revision()) throw conflict("课堂进度已改变，请刷新后重试");
        // SUMMARY is both "this beat is a summary" and "the lesson is over", and a deck's own 小结 page can be
        // followed by the next sub-lesson. Only a cursor past the last step really ends the lesson; treating the
        // derived state as terminal strands the learner on that page with the rest of the deck unreachable.
        if (session.state() == ClassroomState.SUMMARY && current.index() >= steps.size()) throw conflict("本课已经结束");
        if (session.paused() && action != ClassroomAction.RESUME) throw conflict("请先恢复课堂");
        String input = content == null ? "" : content.trim();
        int index = current.index(); boolean paused = session.paused(); String response = current.response();
        ClassroomState nextState = session.state(); ClassroomAnswerEvaluation evaluation = null;
        switch (action) {
            case PAUSE -> paused = true;
            case RESUME -> { if (!paused) throw conflict("课堂未暂停"); paused = false; }
            case FINISH -> { index = steps.size(); nextState = ClassroomState.SUMMARY; response = null; }
            case ASK, ANSWER -> {
                if (input.isBlank()) throw conflict("请填写问题或回答");
                boolean grading = action == ClassroomAction.ANSWER;
                JsonNode previous = response == null ? null : mapper.readTree(response);
                // A wrong answer is not an answer: the question stays open and the learner answers again.
                // Only a question already passed (answered) or a step that is not waiting can refuse input.
                boolean passedBefore = previous != null && previous.path("answered").asBoolean(false);
                if (grading && (session.state() != ClassroomState.WAITING || passedBefore)) throw conflict("当前没有等待作答的问题");
                int attempts = previous == null ? 0 : previous.path("attempts").asInt(0);
                JsonNode active = index >= 0 && index < steps.size() ? steps.get(index) : mapper.createObjectNode();
                ObjectNode context = mapper.createObjectNode();
                ObjectNode stepContext = (ObjectNode) active.deepCopy();
                JsonNode lesson = mapper.readTree(session.scriptJson());
                ObjectNode lessonContext = mapper.createObjectNode();
                lessonContext.set("title", lesson.path("title"));
                lessonContext.set("objectives", lesson.path("objectives"));
                // Evidence is attached to every lesson step for display. Send each textbook chunk
                // only once to the model instead of repeating it in the entire generated script.
                if (lesson.path("textbookSources").isArray()) {
                    lessonContext.set("textbookSources", lesson.path("textbookSources"));
                    stepContext.remove("evidence");
                }
                context.set("currentStep", stepContext); context.set("lesson", lessonContext); context.put("studentInput", input);
                if (response != null) context.set("previousResponse", mapper.readTree(response));
                // The grader knows which attempt this is, so its hint can tighten instead of repeating itself.
                if (grading) context.put("attempt", attempts + 1);
                JsonNode generated = model.generate(userId,
                    grading ? "你是数据结构老师。依据所给教材教案，对学生回答做语义判断，识别同义表达、否定和误区，不用关键词命中判分。返回 {\"status\":\"CORRECT|MISCONCEPTION|INCORRECT\",\"feedback\":\"针对学生回答的解释\",\"misconception\":null}。判为错误时 feedback 要指出答案偏离在哪，并给出能让学生自己纠正的提示，让他重新作答；不要直接把完整答案念出来，也不要说本课已经结束。若 attempt 大于 1，提示要更具体，可以点出关键结论。"
                        : "你是数据结构老师。先直接回答学生此刻的插问，不要复述主线、不要求先答原题。只依据教案提供的教材事实，资料不足时明确说明；最多两小段。返回 {\"feedback\":\"老师的回答\",\"animationRef\":null}。如果学生希望看动画，由你理解其意图并生成或复用当前例子的 animationRef，不能忽略这个请求。格式 {\"protocol\":\"dsvp/1.0\",\"request\":{\"version\":\"1.0\",\"structure\":\"...\",\"operation\":\"...\",\"params\":{},\"initial_state\":{\"data\":[]}}}。\n"
                            + animations.animationRules(session.chapterId())
                            + "无法执行的算法说明限制，不编造轨迹。",
                    context.toString() + "\n动画字段约束：\n" + com.feng.dsagent.animation.DsvpModelContract.INSTRUCTIONS, 1800, json -> {
                        ClassroomModelJson.requireText(json, "feedback");
                        if (grading && !Set.of("CORRECT", "MISCONCEPTION", "INCORRECT").contains(json.path("status").asText())) throw new IllegalArgumentException("$.status 必须是 CORRECT、MISCONCEPTION 或 INCORRECT");
                        if (json.hasNonNull("animationRef")) {
                            if (!json.path("animationRef").path("protocol").asText().equals("dsvp/1.0")) throw new IllegalArgumentException("$.animationRef.protocol 必须是 dsvp/1.0");
                            try { animations.adapt(json.path("animationRef").path("request")); }
                            catch (ApiException error) { throw new IllegalArgumentException("$.animationRef.request: " + error.getMessage() + "; initial_state 必须使用 {\"data\":[...]} 对象结构，params 必须是对象。请保留 feedback 并返回完整 animationRef.request。"); }
                        }
                    });
                // Model output is teaching content, never authority over the runtime cursor or resume target.
                ObjectNode reply = mapper.createObjectNode();
                reply.put("feedback", generated.path("feedback").asText());
                boolean passed = false;
                if (grading) {
                    passed = ClassroomAnswerStatus.CORRECT.name().equals(generated.path("status").asText());
                    attempts++;
                    reply.put("status", generated.path("status").asText());
                    if (generated.path("misconception").isTextual()) reply.put("misconception", generated.path("misconception").asText());
                    reply.put("attempts", attempts);
                    // Wrong is not the end of the question: the learner stays on it and answers again.
                    reply.put("retry", !passed);
                }
                if (generated.hasNonNull("animationRef")) reply.set("animationRef", generated.path("animationRef").deepCopy());
                // If an answer had already been graded, follow-up questions must retain its resume target.
                boolean answered = passed || (!grading && passedBefore);
                reply.put("answered", answered); reply.put("question", input); reply.put("kind", grading ? "answer" : "question");
                if (attempts > 0) reply.put("attempts", attempts);
                response = reply.toString();
                if (grading) {
                    // A wrong answer keeps the cursor on the very same question and leaves it unanswered,
                    // so CONTINUE cannot walk past it: the learner has to answer again to move on.
                    nextState = passed ? ClassroomState.DISCUSS : ClassroomState.WAITING;
                    evaluation = new ClassroomAnswerEvaluation(ClassroomAnswerStatus.valueOf(reply.path("status").asText()), null, reply.path("feedback").asText());
                    events.record(userId, new LearningEventCommand("CLASSROOM_ANSWER", session.chapterId(), id, reply));
                }
            }
            case CONTINUE -> {
                if (response != null && !mapper.readTree(response).path("answered").asBoolean(false)) {
                    response = null; // Explicit return to the exact interrupted step; do not consume it.
                } else {
                    if (session.state() == ClassroomState.WAITING && response == null) throw conflict("请先回答当前问题");
                    index++; response = null;
                    nextState = index >= steps.size() ? ClassroomState.SUMMARY : state(steps.get(index));
                }
            }
        }
        int changed = jdbc.update("UPDATE classroom_sessions SET runtime_index = ?, runtime_revision = runtime_revision + 1, runtime_response = ? WHERE id = ? AND user_id = ? AND runtime_revision = ?", index, response, id, userId, current.revision());
        if (changed != 1) throw conflict("课堂进度已改变，请刷新后重试");
        ClassroomSessionRecord updated = repository.updateSession(session, new ClassroomStatus(nextState, paused), session.summary());
        repository.appendEvent(new ClassroomEventRecord(id, action, input, session.state(), nextState, evaluation));
        return view(updated);
    }
    /** Records the page a teacher or student chose for one step of this lesson. */
    @Transactional
    public ClassroomSessionView pinSlide(long userId, String id, int stepIndex, String slideId) {
        ClassroomSessionRecord session = owned(userId, id);
        String lessonId = mapper.readTree(session.scriptJson()).path("lessonId").asText("");
        slides.pin(userId, session.scriptId(), session.scriptJson(), lessonId, stepIndex, slideId);
        return view(session);
    }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "CLASSROOM_ACTION_INVALID", message); }
}
