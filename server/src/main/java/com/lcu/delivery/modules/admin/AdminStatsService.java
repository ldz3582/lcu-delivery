package com.lcu.delivery.modules.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板 / 资金总览 / 排行统计。
 * 全部用 SQL 聚合，避免把全表捞到内存里算。
 */
@Service
public class AdminStatsService {

    private final JdbcTemplate jdbc;

    public AdminStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private int intOf(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    private double pct(int part, int total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 订单趋势：最近 N 天每天的订单数、GMV（已支付口径）、完成数。 */
    public List<Map<String, Object>> trend(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = d - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("orders", intOf("SELECT COUNT(*) FROM orders WHERE DATE(created_at) = ?", day));
            m.put("paidOrders", intOf("SELECT COUNT(*) FROM orders WHERE DATE(created_at) = ? AND paid_at IS NOT NULL", day));
            m.put("gmv", intOf("SELECT COALESCE(SUM(pay_amount),0) FROM orders WHERE DATE(created_at) = ? AND paid_at IS NOT NULL", day));
            m.put("finished", intOf("SELECT COUNT(*) FROM orders WHERE DATE(created_at) = ? AND status = 50", day));
            rows.add(m);
        }
        return rows;
    }

    /** 商家排行：按已完成订单的 GMV 排序。 */
    public List<Map<String, Object>> shopRanking(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT s.id, s.name, COUNT(o.id) orders, COALESCE(SUM(o.pay_amount),0) gmv, "
                        + "COALESCE(SUM(o.commission),0) commission "
                        + "FROM orders o JOIN shop s ON s.id = o.shop_id "
                        + "WHERE o.status = 50 GROUP BY s.id, s.name ORDER BY gmv DESC LIMIT " + n)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shopId", r.get("id"));
            m.put("name", r.get("name"));
            m.put("orders", ((Number) r.get("orders")).intValue());
            m.put("gmv", ((Number) r.get("gmv")).intValue());
            m.put("commission", ((Number) r.get("commission")).intValue());
            rows.add(m);
        }
        return rows;
    }

    /** 骑手排行：按完成单量排序，附带超时单数与评分。 */
    public List<Map<String, Object>> riderRanking(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT rider_id, COUNT(*) orders, COALESCE(SUM(rider_income),0) income "
                        + "FROM orders WHERE status = 50 AND rider_id IS NOT NULL "
                        + "GROUP BY rider_id ORDER BY orders DESC LIMIT " + n)) {
            Long riderId = ((Number) r.get("rider_id")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("riderId", riderId);
            m.put("orders", ((Number) r.get("orders")).intValue());
            m.put("income", ((Number) r.get("income")).intValue());
            m.put("score", jdbc.queryForObject("SELECT COALESCE(score,5.00) FROM rider WHERE id = ?", BigDecimal.class, riderId));
            m.put("timeoutOrders", intOf("SELECT COALESCE(timeout_orders,0) FROM rider WHERE id = ?", riderId));
            m.put("badReviews", intOf("SELECT COUNT(*) FROM review WHERE rider_id = ? AND rider_score <= 2", riderId));
            rows.add(m);
        }
        return rows;
    }

    /** 关键率值：超时率、差评率、复购率、用户增长。 */
    public Map<String, Object> rates() {
        int finished = intOf("SELECT COUNT(*) FROM orders WHERE status = 50");
        int timeout = intOf("SELECT COALESCE(SUM(timeout_orders),0) FROM rider");
        int reviews = intOf("SELECT COUNT(*) FROM review");
        int badReviews = intOf("SELECT COUNT(*) FROM review WHERE shop_score <= 2 OR rider_score <= 2");
        int users = intOf("SELECT COUNT(*) FROM user");
        int repeatUsers = intOf("SELECT COUNT(*) FROM (SELECT user_id FROM orders GROUP BY user_id HAVING COUNT(*) > 1) t");
        int newToday = intOf("SELECT COUNT(*) FROM user WHERE DATE(created_at) = CURDATE()");
        int new7 = intOf("SELECT COUNT(*) FROM user WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("finishedOrders", finished);
        m.put("timeoutOrders", timeout);
        m.put("timeoutRate", pct(timeout, finished));
        m.put("reviews", reviews);
        m.put("badReviews", badReviews);
        m.put("badReviewRate", pct(badReviews, reviews));
        m.put("totalUsers", users);
        m.put("repeatUsers", repeatUsers);
        m.put("repurchaseRate", pct(repeatUsers, users));
        m.put("newUsersToday", newToday);
        m.put("newUsers7d", new7);
        // 7 日新增占比（new7 / 全部用户）；早期用 new7/(users-new7) 在用户基数小时会算出 3100% 这种失真值
        m.put("newUserShare7d", pct(new7, users));
        return m;
    }

    /** 资金总览：平台佣金、待结算、已结算、提现、退款、配送服务费。 */
    public Map<String, Object> financeOverview() {
        int gmv = intOf("SELECT COALESCE(SUM(pay_amount),0) FROM orders WHERE paid_at IS NOT NULL AND status NOT IN (60,80)");
        int commission = intOf("SELECT COALESCE(SUM(commission),0) FROM orders WHERE paid_at IS NOT NULL AND status NOT IN (60,80)");
        int refunded = intOf("SELECT COALESCE(SUM(amount),0) FROM refund WHERE status = 1");
        int pendingSettle = intOf("SELECT COALESCE(SUM(shop_income),0) FROM settlement WHERE status = 0");
        int settledShop = intOf("SELECT COALESCE(SUM(shop_income),0) FROM settlement WHERE status = 1");
        int settledRider = intOf("SELECT COALESCE(SUM(rider_income),0) FROM settlement WHERE status = 1");
        int reversed = intOf("SELECT COALESCE(SUM(shop_income),0) FROM settlement WHERE status = 2")
                + intOf("SELECT COALESCE(SUM(rider_income),0) FROM settlement WHERE status = 2");
        int withdrawPending = intOf("SELECT COALESCE(SUM(amount),0) FROM withdraw WHERE status IN (0,1)");
        int withdrawPaid = intOf("SELECT COALESCE(SUM(amount),0) FROM withdraw WHERE status = 2");
        int deliveryFee = intOf("SELECT COALESCE(SUM(delivery_fee),0) FROM orders WHERE paid_at IS NOT NULL AND status NOT IN (60,80)");
        int riderPay = intOf("SELECT COALESCE(SUM(rider_income),0) FROM orders WHERE paid_at IS NOT NULL AND status NOT IN (60,80)");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gmv", gmv);
        m.put("platformCommission", commission);
        m.put("deliveryFeeIncome", deliveryFee);
        m.put("riderPay", riderPay);
        m.put("deliveryServiceFee", deliveryFee - riderPay);
        m.put("refunded", refunded);
        m.put("pendingSettle", pendingSettle);
        m.put("settledShop", settledShop);
        m.put("settledRider", settledRider);
        m.put("reversedSettle", reversed);
        m.put("withdrawPending", withdrawPending);
        m.put("withdrawPaid", withdrawPaid);
        // 口径说明：gmv / commission 只统计「已支付且未取消未退款」的订单；
        // 所以计算净利润时只能扣「这些仍计入口径的订单上发生的退款」，否则会把已排除的退款单再扣一次。
        int refundedOnCounted = intOf(
                "SELECT COALESCE(SUM(r.amount),0) FROM refund r JOIN orders o ON o.id = r.order_id "
                        + "WHERE r.status = 1 AND o.status NOT IN (60,80)");
        m.put("refundedOnCounted", refundedOnCounted);
        m.put("platformNetIncome", commission + Math.max(0, deliveryFee - riderPay) - refundedOnCounted);
        return m;
    }

    /** 资金流水：可按类型过滤，分页。 */
    public Map<String, Object> walletFlows(String bizType, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(size, 1), 100);
        String where = (bizType == null || bizType.isBlank()) ? "" : " WHERE f.biz_type = ?";
        Object[] args = (bizType == null || bizType.isBlank()) ? new Object[]{} : new Object[]{bizType};
        int total = intOf("SELECT COUNT(*) FROM wallet_flow f" + where, args);
        List<Object> listArgs = new ArrayList<>(List.of(args));
        listArgs.add(s);
        listArgs.add((p - 1) * s);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT f.id, f.wallet_id, f.biz_type, f.biz_no, f.direction, f.amount, f.balance_after, "
                        + "f.remark, f.created_at, w.owner_type, w.owner_id FROM wallet_flow f "
                        + "LEFT JOIN wallet w ON w.id = f.wallet_id" + where
                        + " ORDER BY f.id DESC LIMIT ? OFFSET ?", listArgs.toArray())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("walletId", r.get("wallet_id"));
            m.put("ownerType", r.get("owner_type"));
            m.put("ownerId", r.get("owner_id"));
            m.put("bizType", r.get("biz_type"));
            m.put("bizNo", r.get("biz_no"));
            m.put("direction", r.get("direction"));
            m.put("amount", r.get("amount"));
            m.put("balanceAfter", r.get("balance_after"));
            m.put("remark", r.get("remark"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", p);
        data.put("size", s);
        data.put("list", rows);
        return data;
    }

    /** 订单导出（CSV，带 BOM 方便 Excel 打开）。 */
    public String exportOrdersCsv(Integer status, String keyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单ID,订单号,商家ID,用户ID,骑手ID,状态,商品金额(元),打包费(元),配送费(元),优惠(元),"
                + "实付(元),平台佣金(元),商家收入(元),骑手收入(元),违约金(元),下单时间\n");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where += " AND o.status = ?";
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where += " AND o.order_no LIKE ?";
            args.add("%" + keyword.trim() + "%");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT o.id, o.order_no, o.shop_id, o.user_id, o.rider_id, o.status, o.goods_amount, "
                        + "o.pack_fee, o.delivery_fee, o.discount_amount, o.pay_amount, o.commission, "
                        + "o.shop_income, o.rider_income, o.penalty_amount, o.created_at "
                        + "FROM orders o" + where + " ORDER BY o.id DESC LIMIT 5000", args.toArray());
        for (Map<String, Object> r : rows) {
            sb.append(r.get("id")).append(',')
                    .append(r.get("order_no")).append(',')
                    .append(r.get("shop_id")).append(',')
                    .append(r.get("user_id")).append(',')
                    .append(r.get("rider_id") == null ? "" : r.get("rider_id")).append(',')
                    .append(statusText(((Number) r.get("status")).intValue())).append(',')
                    .append(yuan(r.get("goods_amount"))).append(',')
                    .append(yuan(r.get("pack_fee"))).append(',')
                    .append(yuan(r.get("delivery_fee"))).append(',')
                    .append(yuan(r.get("discount_amount"))).append(',')
                    .append(yuan(r.get("pay_amount"))).append(',')
                    .append(yuan(r.get("commission"))).append(',')
                    .append(yuan(r.get("shop_income"))).append(',')
                    .append(yuan(r.get("rider_income"))).append(',')
                    .append(yuan(r.get("penalty_amount"))).append(',')
                    .append(r.get("created_at")).append('\n');
        }
        return "\uFEFF" + sb;
    }

    private String yuan(Object fen) {
        if (fen == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(((Number) fen).intValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private String statusText(int status) {
        return switch (status) {
            case 0 -> "待支付";
            case 10 -> "待接单";
            case 20 -> "待出餐";
            case 30 -> "待取餐";
            case 40 -> "配送中";
            case 50 -> "已完成";
            case 60 -> "已取消";
            case 70 -> "售后中";
            case 80 -> "已退款";
            default -> "未知";
        };
    }
}
