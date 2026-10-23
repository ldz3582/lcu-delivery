package com.lcu.delivery.common;

public final class ErrorCode {

    public static final int OK = 0;
    public static final int PARAM = 1001;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;

    public static final int ORDER_STATUS = 2000;
    public static final int OUT_OF_FENCE = 2001;
    public static final int STOCK = 2002;
    public static final int SHOP_CLOSED = 2003;
    public static final int MIN_AMOUNT = 2004;
    public static final int IDENTITY_REQUIRED = 2005;
    public static final int PAY_FAILED = 4001;

    private ErrorCode() {
    }
}
