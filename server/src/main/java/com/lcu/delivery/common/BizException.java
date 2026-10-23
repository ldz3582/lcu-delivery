package com.lcu.delivery.common;

/**
 * 业务异常：code 见 ErrorCode。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(2000, message);
    }

    public int getCode() {
        return code;
    }
}
