package com.feng.dsagent.teacher.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：教师班级与学生可见范围相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum TeacherErrorCode implements ErrorCode {
    STUDENT_NOT_IN_YOUR_CLASS(HttpStatus.FORBIDDEN, "STUDENT_NOT_IN_YOUR_CLASS", "该学生不在你带的班里"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "没有这个学生。"),
    CLASS_NOT_YOURS(HttpStatus.FORBIDDEN, "CLASS_NOT_YOURS", "这个班不在你名下");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    TeacherErrorCode(HttpStatus status, String code, String defaultMessage) {
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
