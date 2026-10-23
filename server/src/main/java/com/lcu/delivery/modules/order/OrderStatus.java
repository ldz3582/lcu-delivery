package com.lcu.delivery.modules.order;

/**
 * 订单状态机（与手册表 3-4 一致）。
 */
public final class OrderStatus {

    public static final int UNPAID = 0;        // 待支付
    public static final int WAIT_ACCEPT = 10;  // 待商家接单
    public static final int COOKING = 20;      // 待出餐
    public static final int WAIT_PICKUP = 30;  // 待骑手取餐
    public static final int DELIVERING = 40;   // 配送中
    public static final int FINISHED = 50;     // 已完成
    public static final int CANCELED = 60;     // 已取消
    public static final int AFTER_SALE = 70;   // 售后中
    public static final int REFUNDED = 80;     // 已退款

    private OrderStatus() {
    }

    public static String text(int status) {
        return switch (status) {
            case UNPAID -> "待支付";
            case WAIT_ACCEPT -> "待接单";
            case COOKING -> "待出餐";
            case WAIT_PICKUP -> "待取餐";
            case DELIVERING -> "配送中";
            case FINISHED -> "已完成";
            case CANCELED -> "已取消";
            case AFTER_SALE -> "售后中";
            case REFUNDED -> "已退款";
            default -> "未知";
        };
    }
}
