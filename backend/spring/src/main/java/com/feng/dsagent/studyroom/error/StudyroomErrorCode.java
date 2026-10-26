package com.feng.dsagent.studyroom.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：学习课堂大纲、片段准备、发布、追问与课堂事件相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum StudyroomErrorCode implements ErrorCode {
    CHAPTER_NOT_FOUND(HttpStatus.BAD_REQUEST, "CHAPTER_NOT_FOUND", "章节不存在"),
    CHECK_ALREADY_PREPARING(HttpStatus.CONFLICT, "CHECK_ALREADY_PREPARING", "正在准备这个知识点的小测"),
    CHECK_NOT_FOUND(HttpStatus.NOT_FOUND, "CHECK_NOT_FOUND", "当前知识点不存在"),
    CHECK_OPTION_INVALID(HttpStatus.CONFLICT, "CHECK_OPTION_INVALID", "请选择有效选项"),
    CHECK_VERSION_CHANGED(HttpStatus.CONFLICT, "CHECK_VERSION_CHANGED", "练习内容已更新，请读取当前版本"),
    CLASSROOM_BUDGET_EXHAUSTED(HttpStatus.CONFLICT, "CLASSROOM_BUDGET_EXHAUSTED", "本次课堂任务预算已用尽，已完成的回答会保留"),
    CLASSROOM_EVENT_SUBSCRIPTIONS_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "CLASSROOM_EVENT_SUBSCRIPTIONS_LIMITED", "课堂进度连接较多，请稍后重试"),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在"),
    FRAGMENT_BUDGET_EXHAUSTED(HttpStatus.CONFLICT, "FRAGMENT_BUDGET_EXHAUSTED", "本片段预算已用尽，请明确重新生成该片段，可能使用模型额度。"),
    FRAGMENT_EVIDENCE_UNAVAILABLE(HttpStatus.CONFLICT, "FRAGMENT_EVIDENCE_UNAVAILABLE", "当前讲解缺少授权依据"),
    FRAGMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "FRAGMENT_NOT_FOUND", "学习片段不存在"),
    FRAGMENT_NOT_FOUND_CONFLICT(HttpStatus.CONFLICT, "FRAGMENT_NOT_FOUND", "学习片段不存在"),
    FRAGMENT_NOT_READY(HttpStatus.CONFLICT, "FRAGMENT_NOT_READY", "请等待当前讲解就绪后追问"),
    FRAGMENT_PLAN_CHANGED(HttpStatus.CONFLICT, "FRAGMENT_PLAN_CHANGED", "片段目标与已确认计划不一致"),
    FRAGMENT_REQUIRED_RESOURCE_MISSING(HttpStatus.CONFLICT, "FRAGMENT_REQUIRED_RESOURCE_MISSING", "必要材料不可用："),
    FRAGMENT_REVISION_CHANGED(HttpStatus.CONFLICT, "FRAGMENT_REVISION_CHANGED", "内容已更新，请刷新后重试"),
    FRAGMENT_REVISION_MISSING(HttpStatus.CONFLICT, "FRAGMENT_REVISION_MISSING", "片段修订不存在"),
    FRAGMENT_UNIT_MISMATCH(HttpStatus.CONFLICT, "FRAGMENT_UNIT_MISMATCH", "片段不属于当前知识点"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "同一请求标识不能用于不同内容"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请为本次操作提供有效的 Idempotency-Key"),
    INVALID_CLASSROOM_EVENT(HttpStatus.BAD_REQUEST, "INVALID_CLASSROOM_EVENT", "课堂事件无效或过大"),
    INVALID_COURSE_TITLE(HttpStatus.BAD_REQUEST, "INVALID_COURSE_TITLE", "课程名称须为 1–160 个字符"),
    INVALID_EVENT_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CURSOR", "进度游标无效"),
    INVALID_OUTLINE_FEEDBACK(HttpStatus.BAD_REQUEST, "INVALID_OUTLINE_FEEDBACK", "请输入修改意见，最多4000字"),
    INVALID_OUTLINE_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_OUTLINE_REQUEST", "请输入问题并选择详细程度"),
    JOB_LEASE_LOST(HttpStatus.CONFLICT, "JOB_LEASE_LOST", "任务租约已失效"),
    JOB_NOT_RUNNING(HttpStatus.CONFLICT, "JOB_NOT_RUNNING", "生成任务已失效"),
    KNOWLEDGE_NOT_COMPLETE(HttpStatus.CONFLICT, "KNOWLEDGE_NOT_COMPLETE", "请先阅读该知识点的后续讲解"),
    LECTURE_REGION_CONFLICT(HttpStatus.CONFLICT, "LECTURE_REGION_CONFLICT", "该区域标识已被使用"),
    LECTURE_REGION_LIMIT(HttpStatus.CONFLICT, "LECTURE_REGION_LIMIT", "每段讲解最多保存 12 个区域"),
    LECTURE_REVISION_CHANGED(HttpStatus.CONFLICT, "LECTURE_REVISION_CHANGED", "讲解内容已更新，请重新打开课堂"),
    LECTURE_TARGET_INVALID(HttpStatus.CONFLICT, "LECTURE_TARGET_INVALID", "区域必须属于当前讲解的可用课件"),
    MODEL_ACTIVE_TIME_EXCEEDED(HttpStatus.CONFLICT, "MODEL_ACTIVE_TIME_EXCEEDED", "本次生成时间已超出上限"),
    MODEL_CALL_UNCERTAIN(HttpStatus.CONFLICT, "MODEL_CALL_UNCERTAIN", "前次模型调用结果不确定，请主动重试"),
    MODEL_OUTPUT_TOO_LARGE(HttpStatus.CONFLICT, "MODEL_OUTPUT_TOO_LARGE", "大纲输出过长"),
    OUTLINE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "OUTLINE_ALREADY_CONFIRMED", "已确认的大纲不能编辑"),
    OUTLINE_EVIDENCE_INVALID(HttpStatus.CONFLICT, "OUTLINE_EVIDENCE_INVALID", "大纲引用了未授权资料"),
    OUTLINE_EVIDENCE_UNAVAILABLE(HttpStatus.CONFLICT, "OUTLINE_EVIDENCE_UNAVAILABLE", "没有可用的授权资料，暂时无法规划学习路线"),
    OUTLINE_GENERATING(HttpStatus.CONFLICT, "OUTLINE_GENERATING", "大纲正在生成，请等待完成"),
    OUTLINE_JOB_STALE(HttpStatus.CONFLICT, "OUTLINE_JOB_STALE", "大纲生成任务已失效"),
    OUTLINE_MATERIALS_UNAVAILABLE(HttpStatus.CONFLICT, "OUTLINE_MATERIALS_UNAVAILABLE", "必要材料不可用，请先修改大纲"),
    OUTLINE_NOT_FOUND(HttpStatus.NOT_FOUND, "OUTLINE_NOT_FOUND", "学习大纲不存在"),
    OUTLINE_PLAN_CHANGED(HttpStatus.CONFLICT, "OUTLINE_PLAN_CHANGED", "讲解安排已改变，请重新审阅大纲"),
    OUTLINE_PREVIEW_INVALID(HttpStatus.CONFLICT, "OUTLINE_PREVIEW_INVALID", "大纲重复定义知识点"),
    OUTLINE_PREVIEW_TOO_LARGE(HttpStatus.CONFLICT, "OUTLINE_PREVIEW_TOO_LARGE", "大纲预览超出上限"),
    OUTLINE_REQUIRED(HttpStatus.CONFLICT, "OUTLINE_REQUIRED", "请先确认学习大纲"),
    OUTLINE_RETRY_UNAVAILABLE(HttpStatus.CONFLICT, "OUTLINE_RETRY_UNAVAILABLE", "当前没有失败的大纲任务"),
    OUTLINE_REVISION_NOT_FOUND(HttpStatus.CONFLICT, "OUTLINE_REVISION_NOT_FOUND", "历史版本不存在"),
    OUTLINE_VERSION_CONFLICT(HttpStatus.CONFLICT, "OUTLINE_VERSION_CONFLICT", "大纲已更新，请重新审阅最新版本"),
    QUESTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "QUESTION_ALREADY_ACTIVE", "当前已有进行中的追问"),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "课堂问题不存在"),
    QUESTION_NOT_FOUND_CONFLICT(HttpStatus.CONFLICT, "QUESTION_NOT_FOUND", "课堂问题不存在"),
    QUESTION_STOPPED(HttpStatus.CONFLICT, "QUESTION_STOPPED", "回答已停止"),
    REPAIR_NOT_APPLICABLE(HttpStatus.CONFLICT, "REPAIR_NOT_APPLICABLE", "该问题已不存在，请刷新最新大纲后重试"),
    REPAIR_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "REPAIR_NOT_SUPPORTED", "该问题无法自动修复，请调整大纲或缩小学习范围"),
    ROOM_RUN_CONFLICT(HttpStatus.CONFLICT, "ROOM_RUN_CONFLICT", "当前学习路线已改变，请重新规划"),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "学习路线不存在"),
    RUN_SUPERSEDED(HttpStatus.CONFLICT, "RUN_SUPERSEDED", "学习路线已更新，请在当前课堂重新提问"),
    STREAM_NOT_FOUND(HttpStatus.NOT_FOUND, "STREAM_NOT_FOUND", "课堂进度不存在"),
    STREAM_STALE_ATTEMPT(HttpStatus.CONFLICT, "STREAM_STALE_ATTEMPT", "生成尝试已更新"),
    STREAM_TERMINAL(HttpStatus.CONFLICT, "STREAM_TERMINAL", "课堂任务已结束"),
    STUDYROOM_CONTENT_INVALID(HttpStatus.CONFLICT, "STUDYROOM_CONTENT_INVALID", "片段缺少展示协议，请重新准备"),
    STUDYROOM_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "STUDYROOM_CONTEXT_INVALID", "当前步骤无效"),
    STUDYROOM_CONTEXT_INVALID_CONFLICT(HttpStatus.CONFLICT, "STUDYROOM_CONTEXT_INVALID", "追问上下文已改变"),
    STUDYROOM_EVIDENCE_UNAVAILABLE(HttpStatus.CONFLICT, "STUDYROOM_EVIDENCE_UNAVAILABLE", "当前追问缺少授权依据，请缩小问题范围"),
    STUDYROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDYROOM_NOT_FOUND", "问答课堂不存在"),
    STUDYROOM_QUESTION_INVALID(HttpStatus.BAD_REQUEST, "STUDYROOM_QUESTION_INVALID", "问题内容无效或过长"),
    STUDYROOM_REPLY_INVALID(HttpStatus.CONFLICT, "STUDYROOM_REPLY_INVALID", "回答超出长度限制"),
    UNIT_NOT_FOUND(HttpStatus.CONFLICT, "UNIT_NOT_FOUND", "知识点不存在");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    StudyroomErrorCode(HttpStatus status, String code, String defaultMessage) {
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
