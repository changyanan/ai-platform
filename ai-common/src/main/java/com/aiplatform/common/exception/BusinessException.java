package com.aiplatform.common.exception;

import com.aiplatform.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 预期内的业务异常，由全局异常处理器转换为统一响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getDefaultMessage(), HttpStatus.BAD_REQUEST);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(ErrorCode errorCode, String message, HttpStatus status) {
        this(errorCode.getCode(), message, status);
    }

    public BusinessException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message, HttpStatus.CONFLICT);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
    }
}
