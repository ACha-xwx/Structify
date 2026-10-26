package com.feng.dsagent.admin.vo;

import com.feng.dsagent.admin.service.AdminUserLearningService.RecentJob;
import com.feng.dsagent.learning.service.LearningMapService;
import com.feng.dsagent.studyroom.library.service.CourseLibraryService;
import java.util.List;
import com.feng.dsagent.admin.service.AdminUserLearningService;

public record AdminUserLearningVO(
    long userId,
    String email,
    String username,
    String status,
    List<CourseLibraryService.Course> courses,
    long bookmarks,
    long classroomSessions,
    long classroomEvents,
    String lastActivityAt,
    long tokensLast30Days,
    long callsLast30Days,
    List<RecentJob> recentJobs,
    LearningMapService.LearningMap learningMap
) {}
