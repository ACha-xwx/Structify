package com.feng.dsagent.admin.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Admin capability error codes. OpenAPI {@code code} strings are stable; do not rename values.
 */
public enum AdminErrorCode implements ErrorCode {
    PAGE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_PAGE_INVALID", "分页参数无效"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "用户不存在"),
    USER_LOOKUP_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "没有这个用户。"),
    CLASS_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到这个邮箱或用户名对应的账号。"),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "这个班级不存在。"),
    CLASS_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "CLASS_NAME_REQUIRED", "班级名称不能为空。"),
    CLASS_TERM_TOO_LONG(HttpStatus.BAD_REQUEST, "CLASS_TERM_TOO_LONG", "学期名称太长。"),
    IDENTIFIER_REQUIRED(HttpStatus.BAD_REQUEST, "IDENTIFIER_REQUIRED", "请填写邮箱或用户名。"),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "ALREADY_ENROLLED", "这个账号已经在这个班里了。"),
    ROLE_IN_CLASS_INVALID(HttpStatus.BAD_REQUEST, "ROLE_IN_CLASS_INVALID", "班内角色只能是学生或教师。"),
    DISABLE_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ADMIN_DISABLE_REASON_REQUIRED", "禁用用户时必须填写原因"),
    AUDIT_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_AUDIT_TIME_RANGE_INVALID", "审计时间范围无效"),
    AUDIT_TIME_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_AUDIT_TIME_INVALID", "时间参数必须是 ISO-8601 UTC 时间"),
    LAST_ADMIN_PROTECTED(HttpStatus.CONFLICT, "ADMIN_LAST_ADMIN_PROTECTED", "不能禁用或降级系统中的最后一个活跃管理员"),
    SELF_MUTATION_FORBIDDEN(HttpStatus.CONFLICT, "ADMIN_SELF_MUTATION_FORBIDDEN", "管理员不能修改自己的角色或启用状态"),
    USER_STATUS_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_USER_STATUS_INVALID", "用户状态必须是 ACTIVE 或 DISABLED"),
    ROLE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_INVALID", "角色无效"),
    ROLE_SET_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_SET_INVALID", "角色集合不能为空"),
    BACKGROUND_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_BACKGROUND_TASK_NOT_FOUND", "后台任务不存在"),
    BACKGROUND_TASK_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT, "ADMIN_BACKGROUND_TASK_RETRY_NOT_ALLOWED", "当前后台任务不能重试"),
    BACKGROUND_TASK_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "ADMIN_BACKGROUND_TASK_CANCEL_NOT_ALLOWED", "只能取消尚未开始的后台任务"),
    BACKGROUND_TASK_STATUS_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_BACKGROUND_TASK_STATUS_INVALID", "后台任务状态无效"),
    BACKGROUND_TASK_TYPE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_BACKGROUND_TASK_TYPE_INVALID", "后台任务类型无效"),
    KNOWLEDGE_SETTINGS_INCOMPLETE(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_SETTINGS_INCOMPLETE", "匹配方式的四个参数都必须填写"),
    KNOWLEDGE_SETTINGS_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_SETTINGS_INVALID", "匹配参数超出允许范围：分数 0-100、条数 1-12、标题权重 0.5-20"),
    KNOWLEDGE_QUERY_REQUIRED(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_QUERY_REQUIRED", "请输入要测试的检索词"),
    KNOWLEDGE_PAGE_LABEL_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_PAGE_LABEL_INVALID", "页签最长 120 个字符"),
    KNOWLEDGE_CHUNK_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_KNOWLEDGE_CHUNK_NOT_FOUND", "知识条目不存在"),
    KNOWLEDGE_CHAPTER_NOT_FOUND(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_CHAPTER_NOT_FOUND", "章节不存在"),
    KNOWLEDGE_AUDIENCE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_AUDIENCE_INVALID", "视角必须是 GUEST、STUDENT 或 TEAM"),
    KNOWLEDGE_REVIEW_STATUS_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_REVIEW_STATUS_INVALID", "审核状态必须是 DRAFT、PENDING、VERIFIED 或 REJECTED"),
    KNOWLEDGE_LICENSE_SCOPE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_LICENSE_SCOPE_INVALID", "授权范围必须是 PUBLIC、CLASSROOM_ONLY 或 TEAM_ONLY"),
    KNOWLEDGE_TEXT_REQUIRED(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_TEXT_REQUIRED", "文本不能为空"),
    KNOWLEDGE_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "ADMIN_KNOWLEDGE_TEXT_TOO_LONG", "文本过长");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    AdminErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
