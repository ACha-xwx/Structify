package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.error.AdminErrorCode;
import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.admin.mapper.AdminUserLearningMapper;
import com.feng.dsagent.learning.service.LearningMapService;
import com.feng.dsagent.studyroom.library.service.CourseLibraryService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

import com.feng.dsagent.admin.vo.AdminUserLearningVO;

/**
 * Everything an administrator needs to understand one learner, in a single read.
 *
 * <p>The users page could always say who somebody is; it could not say what they have done. This view gathers the
 * learning side of one account - courses, bookmarks, classroom work, tokens spent, recent generation jobs and the
 * mastery map itself - so a question like "is this student actually learning" does not take five screens to answer.
 *
 * <p>The map is the same computation the student and their teacher see, and the read is audited under
 * {@code ADMIN_VIEW_LEARNING_MAP}: looking at somebody's learning profile is a privileged act whoever does it.
 */
@Service
public class AdminUserLearningService {

    /** One recent studyroom generation job of the user, with what it cost. */
    public record RecentJob(String id, String type, String status, String createdAt, long tokens) {}

    private static final int RECENT_JOBS = 5;

    private final AdminUserLearningMapper learning;
    private final LearningMapService learningMap;
    private final CourseLibraryService courses;
    private final AdminAuditService audit;

    public AdminUserLearningService(
        AdminUserLearningMapper learning,
        LearningMapService learningMap,
        CourseLibraryService courses,
        AdminAuditService audit
    ) {
        this.learning = learning;
        this.learningMap = learningMap;
        this.courses = courses;
        this.audit = audit;
    }

    public AdminUserLearningVO learning(long userId, long actorUserId, String requestId) {
        AdminUserLearningMapper.UserRowData user = requireUser(userId);
        audit.recordRead(actorUserId, "ADMIN_VIEW_LEARNING_MAP", "USER", String.valueOf(userId), requestId);

        LocalDate since = LocalDate.now().minusDays(29);
        List<RecentJob> jobs = learning.selectRecentJobs(userId, RECENT_JOBS).stream()
            .map(row -> new RecentJob(
                row.id(),
                row.jobType(),
                row.status(),
                timestamp(row.createdAt()),
                row.tokens()
            ))
            .toList();

        return new AdminUserLearningVO(
            user.id(),
            user.email(),
            user.username(),
            user.status(),
            courses.list(userId),
            nullToZero(learning.countBookmarks(userId)),
            nullToZero(learning.countClassroomSessions(userId)),
            nullToZero(learning.countClassroomEvents(userId)),
            timestamp(learning.selectLastActivity(userId)),
            nullToZero(learning.sumTokensSince(userId, since)),
            nullToZero(learning.countCallsSince(userId, since)),
            jobs,
            learningMap.forUser(userId)
        );
    }

    private AdminUserLearningMapper.UserRowData requireUser(long userId) {
        AdminUserLearningMapper.UserRowData row = learning.selectUser(userId);
        if (row == null) {
            throw AdminErrorCode.USER_LOOKUP_NOT_FOUND.toException();
        }
        return row;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
