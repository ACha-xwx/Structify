package com.feng.dsagent.admin.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminClassMapper {

    public List<TeacherNameRow> selectTeacherNames();

    public List<ClassSummaryRow> selectClasses();

    public int insertClass(@Param("id") String id, @Param("name") String name, @Param("term") String term);

    public List<MemberRowData> selectMembers(@Param("classId") String classId);

    public List<Long> findUserIdsByIdentifier(@Param("who") String who);

    public int insertEnrollment(
        @Param("classId") String classId,
        @Param("userId") long userId,
        @Param("roleInClass") String roleInClass
    );

    public int deleteEnrollment(@Param("classId") String classId, @Param("userId") long userId);

    public int deleteClass(@Param("classId") String classId);

    public int insertTeacherRole(@Param("userId") long userId);

    public MemberRowData selectMember(@Param("classId") String classId, @Param("userId") long userId);

    public Long countClass(@Param("classId") String classId);

    public record TeacherNameRow(String classId, String who) {
    }

    public record ClassSummaryRow(String id, String name, String term, long students) {
    }

    public record MemberRowData(long id, String username, String email, String roleInClass, Timestamp joinedAt) {
    }
}
