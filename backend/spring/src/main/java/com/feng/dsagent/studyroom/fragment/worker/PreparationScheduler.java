package com.feng.dsagent.studyroom.fragment.worker;


import com.feng.dsagent.studyroom.error.StudyroomErrorCode;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
import com.feng.dsagent.studyroom.fragment.mapper.FragmentLearningMapper;
import com.feng.dsagent.studyroom.generation.service.GenerationJobService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparationScheduler {
    private final FragmentLearningMapper fragments;
    private final GenerationJobService jobs;

    public PreparationScheduler(FragmentLearningMapper fragments, GenerationJobService jobs) {
        this.fragments = fragments;
        this.jobs = jobs;
    }

    private Map<String, Object> currentRun(long user, String run) {
        var row = fragments.selectOwnedRun(run, user);
        if (row == null)
            throw OutlineJson.error(StudyroomErrorCode.RUN_NOT_FOUND);
        OutlineJson.require(
                fragments.selectRoomCurrentRun(row.get("room_id"), run) != null, StudyroomErrorCode.RUN_SUPERSEDED, "学习路线已更新");
        return row;
    }

    @Transactional
    public void requestFocus(long user, String run, String unit, long sequence) {
        fragments.lockUser(user);
        currentRun(user, run);
        OutlineJson.require(
                fragments.selectUnit(unit, run) != null, StudyroomErrorCode.UNIT_NOT_FOUND);
        var prior = fragments.selectLearningProgressForUpdate(user, run);
        if (prior != null && ((Number) prior.get("focus_sequence")).longValue() >= sequence) return;
        if (prior == null) fragments.insertFocusProgress(user, run, unit, sequence);
        else fragments.updateFocusProgress(unit, sequence, user, run);
        fragments.touchRoomLearningAt(run, user);
        fragments.touchRun(run);
        reconcile(user, run);
    }

    @Transactional
    public void reconcile(long user, String runId) {
        fragments.lockUser(user);
        var run = currentRun(user, runId);
        var units = fragments.selectUnitIds(runId);
        if (units.isEmpty()) return;
        var progress = fragments.selectLearningProgressForUpdate(user, runId);
        String focus =
                progress == null ? (String) units.getFirst().get("id") : (String) progress.get("unit_id");
        int position = 0;
        for (int i = 0; i < units.size(); i++)
            if (units.get(i).get("id").equals(focus)) position = i;
        var desired = new LinkedHashMap<String, Integer>();
        for (var row : fragments.selectFragmentIdsByUnit(focus))
            desired.put((String) row.get("id"), desired.isEmpty() ? 300 : 200);
        if (position + 1 < units.size()) {
            var next = fragments.selectFirstFragmentId(units.get(position + 1).get("id"));
            if (next != null) desired.put((String) next.get("id"), 100);
        }
        for (var job : fragments.selectQueuedFragmentJobs(runId))
            if (!desired.containsKey(job.get("fragment_id"))) {
                fragments.cancelQueuedJob(job.get("id"));
                fragments.resetQueuedFragment(job.get("fragment_id"));
            }
        for (var entry : desired.entrySet()) {
            var fragment = fragments.selectFragmentForUpdate(entry.getKey());
            if (!Set.of("PENDING", "QUEUED").contains(fragment.get("status"))) continue;
            int revision =
                    fragment.get("pending_revision") == null
                            ? 1
                            : ((Number) fragment.get("pending_revision")).intValue();
            if (fragments.selectFragmentVersion(entry.getKey(), revision) == null)
                fragments.insertFragmentVersion(entry.getKey(), revision);
            String key = "fragment:" + entry.getKey() + ":" + revision;
            String job =
                    jobs.enqueue(
                            user,
                            (String) run.get("draft_id"),
                            runId,
                            (String) fragment.get("unit_id"),
                            "FRAGMENT",
                            key,
                            Map.of("revision", revision));
            fragments.bindFragmentJob(entry.getKey(), entry.getValue(), job);
            fragments.queueFragment(revision, entry.getKey());
        }
        fragments.touchRun(runId);
    }
}
