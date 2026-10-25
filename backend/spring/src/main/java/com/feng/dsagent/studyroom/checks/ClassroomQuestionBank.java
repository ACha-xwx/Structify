package com.feng.dsagent.studyroom.checks;

import com.feng.dsagent.studyroom.checks.service.ClassroomCheckService;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads the private delivery format without embedding course questions in the application. */
@Component
public class ClassroomQuestionBank {
    public record BankOption(String key, String text) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Question(String id, String type, String stem, List<BankOption> options,
            List<String> answer, String explanation, List<String> knowledgePoints, String reviewStatus) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Catalog(List<Question> questions) {}
    private final OutlineJson json;
    private final String location;
    private final boolean allowDraft;
    @Value("${studyroom.question-bank.bindings-path:}") private String bindingsPath="";

    public ClassroomQuestionBank(OutlineJson json,
            @Value("${studyroom.question-bank.path:}") String location,
            @Value("${studyroom.question-bank.allow-draft:false}") boolean allowDraft) {
        this.json = json;
        this.location = location;
        this.allowDraft = allowDraft;
    }

    public List<Question> selectBound(String unitId, String title) {
        if(bindingsPath.isBlank())throw new IllegalStateException("BANK_NOT_BOUND");
        try {
            var bindings=json.object(Files.readString(Path.of(bindingsPath)));
            Object ids=bindings.getOrDefault(unitId,bindings.get(title));
            if(!(ids instanceof List<?> values))throw new IllegalStateException("BANK_NOT_BOUND");
            return select(new HashSet<>(values.stream().map(String::valueOf).toList()),3);
        } catch(java.io.IOException error) {throw new IllegalStateException("BANK_UNAVAILABLE",error);}
    }

    /** Exact server-supplied bank IDs only; no model or fuzzy text matching. */
    public List<Question> select(Set<String> knowledgePoints, int limit) {
        if (limit < 1 || limit > 3) throw new IllegalArgumentException("BANK_LIMIT_INVALID");
        if (knowledgePoints == null || knowledgePoints.isEmpty())
            throw new IllegalStateException("BANK_NOT_BOUND");
        if (location.isBlank()) throw new IllegalStateException("BANK_NOT_CONFIGURED");
        try {
            var catalog = json.read(Files.readString(Path.of(location)), Catalog.class);
            if (catalog.questions() == null) throw new IllegalStateException("BANK_INVALID");
            var ids = new HashSet<String>();
            var selected = new ArrayList<Question>();
            for (var q : catalog.questions()) {
                if (q.id() == null || !ids.add(q.id())) throw new IllegalStateException("BANK_ID_INVALID");
                if (!"REVIEWED".equals(q.reviewStatus()) && !(allowDraft && "DRAFT".equals(q.reviewStatus()))) continue;
                if (!"single".equals(q.type()) || q.knowledgePoints() == null
                        || Collections.disjoint(q.knowledgePoints(), knowledgePoints)) continue;
                if (q.answer() == null || q.answer().size() != 1 || q.options() == null)
                    throw new IllegalStateException("BANK_ANSWER_INVALID");
                var proposed = new ClassroomCheckService.Proposed(q.stem(), q.options().stream()
                        .map(o -> new ClassroomCheckService.Option(o.key(), o.text())).toList(),
                        q.answer().getFirst(), q.explanation());
                // Reuse the publication constraints before returning canonical material.
                if (proposed.prompt() == null || proposed.prompt().isBlank()
                        || proposed.analysis() == null || proposed.analysis().isBlank()
                        || proposed.options().size() < 2 || proposed.options().size() > 4
                        || proposed.options().stream().noneMatch(o -> Objects.equals(o.id(), proposed.answerOption())))
                    throw new IllegalStateException("BANK_QUESTION_INVALID");
                selected.add(q);
            }
            return List.copyOf(selected.subList(0, Math.min(limit, selected.size())));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("BANK_UNAVAILABLE", error);
        }
    }
}
