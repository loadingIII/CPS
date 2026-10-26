package com.campus.repair.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码。httpStatus 仅 401/403/500 特殊，其余业务错误统一 HTTP 200。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    OK(0, "ok", 200),
    PARAM_ERROR(40001, "参数校验失败", 200),
    UNAUTHORIZED(40101, "未登录或登录已过期", 401),
    FORBIDDEN(40301, "无权限执行该操作", 403),
    NOT_FOUND(40401, "资源不存在", 200),
    CONFLICT(40901, "状态冲突", 200),
    SYSTEM_ERROR(50000, "系统异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
