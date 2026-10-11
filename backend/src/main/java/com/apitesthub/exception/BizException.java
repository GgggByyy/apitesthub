package com.apitesthub.exception;

/**
 * 业务异常：code 会同时作为 HTTP 状态码与响应体 code 返回。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
