package com.feng.dsagent.studyroom.library.service;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.library.mapper.CourseLibraryMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owned library projection. Publication timestamps deliberately do not define user activity. */
@Service
public class CourseLibraryService {
    private final CourseLibraryMapper library;
    private final OutlineJson json;

    public CourseLibraryService(CourseLibraryMapper library, OutlineJson json) {
        this.library = library;
        this.json = json;
    }

    public record Course(
            String id,
            String title,
            String status,
            String roomId,
            String runId,
            int totalPoints,
            int completedPoints,
            int readableFragments,
            String lastPointTitle,
            Instant lastLearningAt,
            Instant activityAt) {}

    @Transactional(readOnly = true)
    public List<Course> list(long user) {
        var courses = new ArrayList<Course>();
        for (var draft : library.selectLibraryDrafts(user)) {
            String run = (String) draft.get("confirmed_run_id");
            var room = run == null ? null : library.selectRoomByCurrentRun(run, user);
            int total = 0, completed = 0, readable = 0, failed = 0;
            String point = null;
            Instant learning = null;
            if (room != null) {
                total = library.countUnits(run);
                completed = library.countCompletedKnowledge(run, user);
                readable = library.countReadableFragments(run);
                failed = library.countFailedFragments(run);
                var progress = library.selectLearningProgress(user, run);
                if (room.get("last_learning_at") != null) learning = instant(room.get("last_learning_at"));
                if (progress != null) {
                    if (learning == null && progress.get("fragment_id") != null)
                        learning = instant(progress.get("updated_at"));
                    point = (String) json.object((String) progress.get("unit_spec_json")).get("title");
                }
            }
            String title = (String) draft.get("display_title");
            if (title == null && room != null) title = (String) room.get("title");
            if (title == null) {
                var revision =
                        library.selectRevision(
                                (String) draft.get("id"),
                                ((Number) draft.get("latest_revision")).intValue());
                if (revision != null)
                    title = (String) json.object((String) revision.get("outline_json")).get("title");
            }
            if (title == null) {
                title = (String) draft.get("question");
                if (title.length() > 160) title = title.substring(0, 160);
            }
            String state =
                    room == null
                            ? "DRAFT"
                            : total > 0 && completed == total
                                    ? "COMPLETED"
                                    : failed > 0
                                            ? "NEEDS_ATTENTION"
                                            : readable > 0 ? "LEARNING" : "PREPARING";
            Instant activity = instant(draft.get("activity_at"));
            if (learning != null && learning.isAfter(activity)) activity = learning;
            courses.add(
                    new Course(
                            (String) draft.get("id"),
                            title,
                            state,
                            room == null ? null : (String) room.get("id"),
                            run,
                            total,
                            completed,
                            readable,
                            point,
                            learning,
                            activity));
        }
        courses.sort(Comparator.comparing(Course::activityAt).reversed().thenComparing(Course::id));
        return List.copyOf(courses);
    }

    private static Instant instant(Object value) {
        return ((Timestamp) value).toInstant();
    }

    @Transactional
    public void rename(long user, String id, String title) {
        if (title == null || title.isBlank() || title.trim().length() > 160)
            throw OutlineJson.error(
                    HttpStatus.BAD_REQUEST, "INVALID_COURSE_TITLE", "课程名称须为 1–160 个字符");
        library.lockUser(user);
        visible(user, id);
        library.updateDisplayTitle(title.trim(), id, user);
    }

    private Map<String, Object> visible(long user, String id) {
        var draft = library.selectOwnedDraft(user, id);
        if (draft == null)
            throw OutlineJson.error(HttpStatus.NOT_FOUND, "OUTLINE_NOT_FOUND", "学习大纲不存在");
        if (draft.get("confirmed_run_id") != null
                && library.selectRoomByCurrentRun((String) draft.get("confirmed_run_id"), user)
                        == null)
            throw OutlineJson.error(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在");
        return draft;
    }

    @Transactional
    public void delete(long user, String id) {
        library.lockUser(user);
        var owned = library.selectDraftForUpdate(user, id);
        if (owned == null)
            throw OutlineJson.error(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在");
        if (owned.get("deleted_at") != null) return;
        var draft = visible(user, id);
        // An unconfirmed revision is only a draft: deleting it must not delete its source course.
        String room =
                draft.get("confirmed_run_id") == null ? null : (String) draft.get("source_room_id");
        if (room == null) {
            library.cancelJobsByDraftId(id);
            library.softDeleteDraft(id);
        } else {
            library.cancelJobsBySourceRoom(room);
            library.cancelRunsByRoom(room, user);
            library.softDeleteDraftsBySourceRoom(room, user);
            library.softDeleteRoom(room, user);
        }
    }
}
