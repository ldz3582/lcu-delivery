package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台运营动作：用户封禁/看单、商家编辑/冻结/处罚/营收、骑手封禁、商品强制下架。
 * 对应验收报告 A2/A3/A4/A5/A17 的缺口。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOpsController {

    private final JdbcTemplate jdbc;

    public AdminOpsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- 用户 ----------------

    @GetMapping("/users/{id}/orders")
    public Result<List<Map<String, Object>>> userOrders(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "50") int size) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, order_no, shop_id, status, pay_amount, created_at FROM orders "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT ?", id, Math.min(Math.max(size, 1), 200))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("orderNo", r.get("order_no"));
            m.put("shopId", r.get("shop_id"));
            m.put("status", r.get("status"));
            m.put("statusText", orderStatusText(((Number) r.get("status")).intValue()));
            m.put("payAmount", r.get("pay_amount"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    /** 封禁/解封用户：封禁后不能下单（IdentityService.requireVerified 之外再补一道 business 校验在 OrderService 里）。 */
    @PostMapping("/users/{id}/ban")
    public Result<Void> banUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int status = ((Number) body.getOrDefault("status", 0)).intValue() == 1 ? 1 : 0;
        jdbc.update("UPDATE `user` SET status = ?, updated_at = NOW() WHERE id = ?", status, id);
        return Result.ok();
    }

    // ---------------- 商家 ----------------

    public record ShopEditReq(String name, String contactPhone, String openTime, String notice,
                              Integer minAmount, Integer packFee, Integer deliveryFee,
                              String commissionRate, Integer categoryId, Integer campus,
                              Integer deliveryRangeMeters) {
    }

    @PutMapping("/shops/{id}")
    public Result<Void> editShop(@PathVariable Long id, @RequestBody ShopEditReq req) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM shop WHERE id = ? AND deleted_at IS NULL", Integer.class, id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在");
        }
        if (req.commissionRate() != null) {
            try {
                double rate = Double.parseDouble(req.commissionRate());
                if (rate < 0 || rate > 1) {
                    throw new BizException(ErrorCode.PARAM, "佣金比例必须在 0~1 之间（如 0.06 表示 6%）");
                }
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM, "佣金比例格式不正确");
            }
        }
        jdbc.update("UPDATE shop SET name = COALESCE(?, name), contact_phone = COALESCE(?, contact_phone), "
                        + "open_time = COALESCE(?, open_time), notice = COALESCE(?, notice), "
                        + "min_amount = COALESCE(?, min_amount), pack_fee = COALESCE(?, pack_fee), "
                        + "delivery_fee = COALESCE(?, delivery_fee), commission_rate = COALESCE(?, commission_rate), "
                        + "category_id = COALESCE(?, category_id), campus = COALESCE(?, campus), "
                        + "delivery_range_meters = COALESCE(?, delivery_range_meters), updated_at = NOW() "
                        + "WHERE id = ?",
                req.name(), req.contactPhone(), req.openTime(), req.notice(),
                req.minAmount(), req.packFee(), req.deliveryFee(),
                req.commissionRate() == null ? null : new java.math.BigDecimal(req.commissionRate()),
                req.categoryId(), req.campus(), req.deliveryRangeMeters(), id);
        return Result.ok();
    }

    /**
     * 冻结/解冻店铺：冻结后商家不能接单、用户端不可见。
     * 语义收紧：只动「已通过(1) ↔ 已冻结(3)」两种状态，
     * 未审核(0)/已驳回(2) 的店不会被顺手改成通过；解冻后回到「休息」，由商家自己开张。
     */
    @PostMapping("/shops/{id}/freeze")
    public Result<Void> freezeShop(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean frozen = Boolean.TRUE.equals(body.get("frozen")) || "1".equals(String.valueOf(body.get("frozen")));
        Integer audit = jdbc.queryForObject("SELECT audit_status FROM shop WHERE id = ?", Integer.class, id);
        if (audit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家不存在");
        }
        if (frozen) {
            if (audit != 1) {
                throw new BizException(ErrorCode.ORDER_STATUS,
                        audit == 3 ? "该商家已处于冻结状态" : "只有「已通过」的商家才能冻结（当前状态：" + audit + "）");
            }
            jdbc.update("UPDATE shop SET audit_status = 3, biz_status = 0, updated_at = NOW() WHERE id = ?", id);
        } else {
            if (audit != 3) {
                throw new BizException(ErrorCode.ORDER_STATUS, "该商家当前不是冻结状态，无需解冻");
            }
            jdbc.update("UPDATE shop SET audit_status = 1, biz_status = 0, updated_at = NOW() WHERE id = ?", id);
        }
        return Result.ok();
    }

    @GetMapping("/shops/{id}/orders")
    public Result<List<Map<String, Object>>> shopOrders(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "50") int size) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, order_no, user_id, status, pay_amount, commission, shop_income, created_at "
                        + "FROM orders WHERE shop_id = ? ORDER BY id DESC LIMIT ?", id, Math.min(Math.max(size, 1), 200))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("orderNo", r.get("order_no"));
            m.put("userId", r.get("user_id"));
            m.put("status", r.get("status"));
            m.put("statusText", orderStatusText(((Number) r.get("status")).intValue()));
            m.put("payAmount", r.get("pay_amount"));
            m.put("commission", r.get("commission"));
            m.put("shopIncome", r.get("shop_income"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    /** 商家营收：今日/本周/本月 + 处罚次数。 */
    @GetMapping("/shops/{id}/revenue")
    public Result<Map<String, Object>> shopRevenue(@PathVariable Long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", revenueOf(id, LocalDate.now().atStartOfDay()));
        m.put("week", revenueOf(id, LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()));
        m.put("month", revenueOf(id, LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        m.put("penalties", jdbc.queryForList(
                "SELECT id, type, amount, remark, created_at FROM merchant_penalty WHERE shop_id = ? ORDER BY id DESC LIMIT 50", id));
        return Result.ok(m);
    }

    private Map<String, Object> revenueOf(Long shopId, LocalDateTime from) {
        Map<String, Object> m = new LinkedHashMap<>();
        Long orders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE shop_id = ? AND status = 50 AND created_at >= ?", Long.class, shopId, from);
        Integer gmv = jdbc.queryForObject(
                "SELECT COALESCE(SUM(pay_amount),0) FROM orders WHERE shop_id = ? AND status = 50 AND created_at >= ?",
                Integer.class, shopId, from);
        Integer commission = jdbc.queryForObject(
                "SELECT COALESCE(SUM(commission),0) FROM orders WHERE shop_id = ? AND status = 50 AND created_at >= ?",
                Integer.class, shopId, from);
        m.put("orders", orders);
        m.put("gmv", gmv);
        m.put("commission", commission);
        return m;
    }

    public record PenaltyReq(Integer type, Integer amount, String remark, Long orderId) {
    }

    /** 商家违规处罚：1 警告 2 罚款 3 下架商品 4 暂停营业。 */
    @PostMapping("/shops/{id}/penalty")
    public Result<Long> penalty(@PathVariable Long id, @RequestBody PenaltyReq req) {
        int type = req.type() == null ? 1 : req.type();
        if (type < 1 || type > 4) {
            throw new BizException(ErrorCode.PARAM, "处罚类型不合法（1 警告 2 罚款 3 下架商品 4 暂停营业）");
        }
        int amount = type == 2 ? Math.max(0, req.amount() == null ? 0 : req.amount()) : 0;
        jdbc.update("INSERT INTO merchant_penalty (shop_id, type, amount, remark, order_id, operator_id, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                id, type, amount, req.remark(), req.orderId(), UserContext.userId(), LocalDateTime.now());
        Long pid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (type == 3) {
            // 下架该店全部在售商品
            jdbc.update("UPDATE product SET status = 0, updated_at = NOW() WHERE shop_id = ? AND deleted_at IS NULL", id);
        } else if (type == 4) {
            // 暂停营业
            jdbc.update("UPDATE shop SET biz_status = 0, updated_at = NOW() WHERE id = ?", id);
        } else if (type == 2 && amount > 0) {
            // 罚款要真的扣：从商家钱包扣（余额不足扣到 0，并在流水备注里记欠款）
            Long walletId = null;
            try {
                walletId = jdbc.queryForObject(
                        "SELECT id FROM wallet WHERE owner_type = 2 AND owner_id = ?", Long.class, id);
            } catch (Exception ignored) {
                // 没有钱包就新建一个 0 余额的，保证罚款有账可查
            }
            if (walletId == null) {
                jdbc.update("INSERT INTO wallet (owner_type, owner_id, balance, frozen, version, updated_at) "
                        + "VALUES (2, ?, 0, 0, 0, NOW())", id);
                walletId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            }
            Integer balance = jdbc.queryForObject("SELECT balance FROM wallet WHERE id = ?", Integer.class, walletId);
            int bal = balance == null ? 0 : balance;
            int actual = Math.min(bal, amount);
            int after = bal - actual;
            jdbc.update("UPDATE wallet SET balance = ?, version = version + 1, updated_at = NOW() WHERE id = ?",
                    after, walletId);
            jdbc.update("INSERT INTO wallet_flow (wallet_id, biz_type, biz_no, direction, amount, balance_after, "
                            + "remark, created_at) VALUES (?,?,?,?,?,?,?,?)",
                    walletId, "SHOP_PENALTY", String.valueOf(pid), -1, actual, after,
                    actual < amount
                            ? "违规罚款（应扣 " + amount + " 分，实扣 " + actual + " 分，欠 " + (amount - actual) + " 分）"
                            : "违规罚款",
                    LocalDateTime.now());
        }
        return Result.ok(pid);
    }

    @PostMapping("/products/{id}/offline")
    public Result<Void> offlineProduct(@PathVariable Long id) {
        jdbc.update("UPDATE product SET status = 0, updated_at = NOW() WHERE id = ?", id);
        return Result.ok();
    }

    /** 商家在售/全部商品（后台做「违规下架」时先看清单）。 */
    @GetMapping("/shops/{id}/products")
    public Result<List<Map<String, Object>>> shopProducts(@PathVariable Long id) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, name, price, special_price, stock, status, sales FROM product "
                        + "WHERE shop_id = ? AND deleted_at IS NULL ORDER BY status DESC, id DESC LIMIT 200", id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("price", r.get("price"));
            m.put("specialPrice", r.get("special_price"));
            m.put("stock", r.get("stock"));
            m.put("status", r.get("status"));
            m.put("statusText", ((Number) r.get("status")).intValue() == 1 ? "在售" : "已下架");
            m.put("sales", r.get("sales"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    // ---------------- 骑手 ----------------

    @PostMapping("/riders/{id}/status")
    public Result<Void> riderStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int status = ((Number) body.getOrDefault("status", 1)).intValue() == 1 ? 1 : 0;
        Object reason = body.get("reason");
        jdbc.update("UPDATE rider SET status = ?, ban_reason = ?, updated_at = NOW() WHERE id = ?",
                status, status == 1 ? null : (reason == null ? null : String.valueOf(reason)), id);
        return Result.ok();
    }

    // ---------------- 工具 ----------------

    private String orderStatusText(int status) {
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
