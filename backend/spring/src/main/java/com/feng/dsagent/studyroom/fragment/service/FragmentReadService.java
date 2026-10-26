package com.feng.dsagent.studyroom.fragment.service;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.compiler.support.CodeExecutionReadiness;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentReadMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentReadService {
    private final FragmentReadMapper fragments;
    private final OutlineJson json;
    private final CodeExecutionReadiness execution;

    public FragmentReadService(
            FragmentReadMapper fragments, OutlineJson json, CodeExecutionReadiness execution) {
        this.fragments = fragments;
        this.json = json;
        this.execution = execution;
    }

    public Map<String, Object> current(long user, String room) {
        var row = fragments.selectCurrentRunId(room, user);
        if (row == null)
            throw OutlineJson.error(StudyroomErrorCode.STUDYROOM_NOT_FOUND, "学习空间不存在");
        OutlineJson.require(row.get("current_run_id") != null, StudyroomErrorCode.OUTLINE_REQUIRED);
        return get(user, (String) row.get("current_run_id"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long user, String id) {
        var run = fragments.selectOwnedRun(id, user);
        if (run == null)
            throw OutlineJson.error(StudyroomErrorCode.RUN_NOT_FOUND);
        List<Map<String, Object>> points = new ArrayList<>();
        String first = null;
        for (var unit : fragments.selectUnits(id)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (var fragment : fragments.selectFragmentsByUnit((String) unit.get("id"))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", fragment.get("id"));
                item.put("key", fragment.get("fragment_key"));
                item.put("order", fragment.get("fragment_order"));
                item.put("status", fragment.get("status"));
                item.put("stepId", fragment.get("step_id"));
                item.put("contentRevision", fragment.get("content_revision"));
                String failureMessage = "这段内容未准备完成，已就绪的内容仍可阅读。";
                if (fragment.get("issues_json") != null) {
                    var issues = json.mapper().readTree((String) fragment.get("issues_json"));
                    if (issues.isArray() && !issues.isEmpty())
                        failureMessage = issues.get(0).path("message").asString(failureMessage);
                }
                item.put(
                        "error",
                        fragment.get("error_code") == null
                                ? null
                                : Map.of(
                                        "code",
                                        fragment.get("error_code"),
                                        "message",
                                        failureMessage,
                                        "retryable",
                                        !"FRAGMENT_BUDGET_EXHAUSTED"
                                                .equals(fragment.get("error_code"))));
                items.add(item);
                if (first == null && fragment.get("step_id") != null) first = (String) fragment.get("id");
            }
            long ready = items.stream().filter(f -> f.get("stepId") != null).count();
            long failed = items.stream().filter(f -> f.get("status").equals("FAILED")).count();
            boolean pending =
                    items.stream()
                            .anyMatch(
                                    f ->
                                            Set.of("PENDING", "QUEUED", "GENERATING")
                                                    .contains(f.get("status")));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", unit.get("id"));
            point.put("order", unit.get("unit_order"));
            var spec = json.mapper().readTree((String) unit.get("unit_spec_json"));
            point.put("title", spec.path("title").asString((String) unit.get("unit_key")));
            point.put(
                    "status",
                    ready == items.size() && !items.isEmpty() && !pending && failed == 0
                            ? "READY"
                            : ready > 0 ? "PARTIAL" : failed > 0 ? "NEEDS_ATTENTION" : "PREPARING");
            point.put("fragments", items);
            point.put("readableFragmentCount", ready);
            point.put("failedFragmentCount", failed);
            point.put("hasPendingContent", pending);
            point.put(
                    "completed",
                    fragments.selectCompletedKnowledge(user, id, (String) unit.get("id")) != null);
            points.add(point);
        }
        var progress = fragments.selectLearningProgress(user, id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("roomId", run.get("room_id"));
        view.put("status", run.get("status"));
        view.put("outline", json.value((String) run.get("approved_outline_json")));
        view.put("codeExecutionAvailable", execution.available());
        view.put("stateVersion", run.get("state_version"));
        view.put("firstReadableFragmentId", first);
        view.put("knowledgePoints", points);
        Map<String, Object> cursor = null;
        if (progress != null) {
            cursor = new LinkedHashMap<>();
            cursor.put("unitId", progress.get("unit_id"));
            cursor.put("fragmentId", progress.get("fragment_id"));
            cursor.put("completed", progress.get("completed"));
            cursor.put("focusSequence", progress.get("focus_sequence"));
        }
        view.put("progress", cursor);
        return view;
    }
}
