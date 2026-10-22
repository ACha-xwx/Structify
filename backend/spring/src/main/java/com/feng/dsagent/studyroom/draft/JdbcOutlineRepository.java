package com.feng.dsagent.studyroom.draft;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class JdbcOutlineRepository {
    private final JdbcTemplate db;

    public JdbcOutlineRepository(JdbcTemplate db) {
        this.db = db;
    }

    public JdbcTemplate db() {
        return db;
    }

    public Map<String, Object> row(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> owned(long user, String id, boolean lock) {
        var row =
                row(
                        "SELECT * FROM study_outline_drafts WHERE id=? AND user_id=? AND deleted_at IS NULL"
                                + (lock ? " FOR UPDATE" : ""),
                        id,
                        user);
        if (row == null)
            throw OutlineJson.error(HttpStatus.NOT_FOUND, "OUTLINE_NOT_FOUND", "学习大纲不存在");
        return row;
    }

    public Map<String, Object> revision(String draft, int revision) {
        return row(
                "SELECT * FROM study_outline_revisions WHERE draft_id=? AND revision=?",
                draft,
                revision);
    }

    public void lockUser(long user) {
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
    }

    public String key(long user, String operation, String key, String payload) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw OutlineJson.error(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "请为本次操作提供有效的 Idempotency-Key");
        var prior =
                row(
                        "SELECT * FROM studyroom_request_keys WHERE user_id=? AND operation=? AND"
                            + " idempotency_key=?",
                        user,
                        operation,
                        key);
        if (prior == null) return null;
        OutlineJson.require(
                Objects.equals(prior.get("payload_hash"), OutlineJson.hash(payload)),
                "IDEMPOTENCY_CONFLICT",
                "同一请求标识不能用于不同内容");
        return (String) prior.get("result_id");
    }

    public void saveKey(long user, String operation, String key, String payload, String result) {
        db.update(
                "INSERT INTO"
                    + " studyroom_request_keys(user_id,operation,idempotency_key,payload_hash,result_id)"
                    + " VALUES(?,?,?,?,?)",
                user,
                operation,
                key,
                OutlineJson.hash(payload),
                result);
    }

    public void message(String draft, String role, String content, int revision) {
        int order =
                db.queryForObject(
                        "SELECT COALESCE(MAX(message_order),-1)+1 FROM study_outline_messages WHERE"
                            + " draft_id=?",
                        Integer.class,
                        draft);
        db.update(
                "INSERT INTO"
                    + " study_outline_messages(id,draft_id,message_order,role,content,revision)"
                    + " VALUES(?,?,?,?,?,?)",
                OutlineJson.id(),
                draft,
                order,
                role,
                content,
                revision);
    }

    public static int number(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).intValue();
    }
}
