package com.campus.repair.common;

import lombok.Getter;

/**
 * 业务异常：携带错误码与提示信息，由全局异常处理器统一转换成响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BusinessException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
        this.httpStatus = ec.getHttpStatus();
    }

    public BusinessException(ErrorCode ec, String message) {
        super(message);
        this.code = ec.getCode();
        this.httpStatus = ec.getHttpStatus();
    }

    public BusinessException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException param(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }
}
