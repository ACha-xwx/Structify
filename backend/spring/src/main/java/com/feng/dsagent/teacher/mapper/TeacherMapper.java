package com.feng.dsagent.teacher.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeacherMapper {

    public List<ClassSummaryRow> selectAllClasses();

    public List<ClassSummaryRow> selectClassesForTeacher(@Param("callerId") long callerId);

    public List<StudentRowData> selectStudents(@Param("classId") String classId);

    public List<UserIdentityRow> selectUserIdentity(@Param("userId") long userId);

    public Long countBookmarks(@Param("userId") long userId);

    public Long countClassroomSessions(@Param("userId") long userId);

    public Long countClassroomEvents(@Param("userId") long userId);

    public Timestamp selectLastActivity(@Param("userId") long userId);

    public Long countTeachingEnrollment(@Param("classId") String classId, @Param("callerId") long callerId);

    public Long countSharedClass(@Param("callerId") long callerId, @Param("studentId") long studentId);

    public record ClassSummaryRow(String id, String name, String term, long students) {
    }

    public record StudentRowData(
        long id,
        String username,
        String email,
        String status,
        Timestamp lastActivity,
        long steps
    ) {
    }

    public record UserIdentityRow(String username, String email) {
    }
}
