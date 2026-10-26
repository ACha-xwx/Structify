package com.feng.dsagent.studyroom.converter;

import com.feng.dsagent.studyroom.entity.StudyroomRecordsEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.mapstruct.Mapper;

/**
 * 职责：将问答课堂查询行映射为房间 / 步骤 / 消息记录实体。
 */
@Mapper(componentModel = "spring")
public abstract class StudyroomRecordConverter {

    public StudyroomRecordsEntity.Room toRoom(Map<String, Object> row) {
        return new StudyroomRecordsEntity.Room(
            (String) row.get("id"),
            ((Number) row.get("user_id")).longValue(),
            (String) row.get("chapter_id"),
            (String) row.get("title"),
            (String) row.get("initial_question"),
            (String) row.get("summary"),
            (String) row.get("current_step_id"),
            ((Number) row.get("version")).intValue(),
            instant(row.get("created_at")),
            instant(row.get("updated_at"))
        );
    }

    public StudyroomRecordsEntity.Step toStep(Map<String, Object> row) {
        return new StudyroomRecordsEntity.Step(
            (String) row.get("id"),
            (String) row.get("step_key"),
            (String) row.get("step_type"),
            (String) row.get("title"),
            (String) row.get("explanation"),
            (String) row.get("evidence_json"),
            (String) row.get("resources_json"),
            ((Number) row.get("step_order")).intValue(),
            Boolean.TRUE.equals(row.get("supplemental")) || Objects.equals(row.get("supplemental"), 1),
            (String) row.get("parent_step_id"),
            instant(row.get("created_at")),
            (String) row.get("orchestration_json")
        );
    }

    public StudyroomRecordsEntity.Message toMessage(Map<String, Object> row) {
        return new StudyroomRecordsEntity.Message(
            (String) row.get("id"),
            (String) row.get("role"),
            (String) row.get("content"),
            (String) row.get("sources_json"),
            (String) row.get("step_id"),
            instant(row.get("created_at"))
        );
    }

    protected Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp: " + value.getClass());
    }
}
