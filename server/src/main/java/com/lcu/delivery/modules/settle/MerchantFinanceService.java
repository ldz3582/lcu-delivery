package com.lcu.delivery.modules.settle;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.pay.PayoutGateway;
import com.lcu.delivery.modules.settle.entity.Settlement;
import com.lcu.delivery.modules.settle.entity.Wallet;
import com.lcu.delivery.modules.settle.entity.WalletFlow;
import com.lcu.delivery.modules.settle.mapper.SettlementMapper;
import com.lcu.delivery.modules.settle.mapper.WalletFlowMapper;
import com.lcu.delivery.modules.settle.mapper.WalletMapper;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家资金：钱包、对账单、营收统计、提现申请。
 *
 * 与结算的关系：订单完成 → 生成 settlement（待结算）→ SettleJob 按 T+N 入钱包（SHOP_SETTLE 流水）。
 * 提现采用「申请即冻结」：可用余额转冻结，平台审核通过=打款完成（真实环境接微信商家转账）。
 */
@Service
public class MerchantFinanceService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int OWNER_SHOP = 2;

    private final SettlementMapper settlementMapper;
    private final WalletMapper walletMapper;
    private final WalletFlowMapper flowMapper;
    private final ShopMapper shopMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService config;
    private final PayoutGateway payoutGateway;

    public MerchantFinanceService(SettlementMapper settlementMapper, WalletMapper walletMapper,
                                  WalletFlowMapper flowMapper, ShopMapper shopMapper, OrderMapper orderMapper,
                                  JdbcTemplate jdbc, SysConfigService config, PayoutGateway payoutGateway) {
        this.payoutGateway = payoutGateway;
        this.settlementMapper = settlementMapper;
        this.walletMapper = walletMapper;
        this.flowMapper = flowMapper;
        this.shopMapper = shopMapper;
        this.orderMapper = orderMapper;
        this.jdbc = jdbc;
        this.config = config;
    }

    // ---------------- 钱包 ----------------

    public Map<String, Object> wallet(Long userId) {
        Shop shop = requireShop(userId);
        Wallet w = walletOf(shop.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", shop.getId());
        m.put("balance", w == null ? 0 : w.getBalance());
        m.put("frozen", w == null ? 0 : w.getFrozen());
        int walletId = w == null ? 0 : w.getId().intValue();
        m.put("totalIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'SHOP_SETTLE'", walletId));
        m.put("todayIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'SHOP_SETTLE' AND created_at >= ?",
                walletId, LocalDate.now().atStartOfDay()));
        m.put("monthIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'SHOP_SETTLE' AND created_at >= ?",
                walletId, LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        m.put("minWithdraw", config.getInt("settle.min_withdraw", 1000));

        List<Map<String, Object>> flows = new ArrayList<>();
        if (walletId != 0) {
            for (WalletFlow f : flowMapper.selectList(Wrappers.<WalletFlow>lambdaQuery()
                    .eq(WalletFlow::getWalletId, walletId)
                    .orderByDesc(WalletFlow::getId)
                    .last("limit 20"))) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("id", f.getId());
                fm.put("bizType", f.getBizType());
                fm.put("bizNo", f.getBizNo());
                fm.put("direction", f.getDirection());
                fm.put("amount", f.getAmount());
                fm.put("balanceAfter", f.getBalanceAfter());
                fm.put("remark", f.getRemark());
                fm.put("createdAt", f.getCreatedAt() == null ? null : f.getCreatedAt().format(TS));
                flows.add(fm);
            }
        }
        m.put("flows", flows);
        return m;
    }

    // ---------------- 对账单 ----------------

    public Map<String, Object> statements(Long userId, LocalDate from, LocalDate to, int page, int size) {
        Shop shop = requireShop(userId);
        LocalDateTime start = (from == null ? LocalDate.now().withDayOfMonth(1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).atTime(LocalTime.MAX);

        var qw = Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getShopId, shop.getId())
                .ge(Settlement::getCreatedAt, start)
                .le(Settlement::getCreatedAt, end)
                .orderByDesc(Settlement::getId);
        Page<Settlement> p = settlementMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Settlement s : p.getRecords()) {
            Order o = s.getOrderId() == null ? null : orderMapper.selectById(s.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("orderId", s.getOrderId());
            m.put("orderNo", o == null ? null : o.getOrderNo());
            m.put("goodsAmount", s.getGoodsAmount());
            m.put("packFee", o == null ? 0 : o.getPackFee());
            m.put("commission", s.getCommission());
            m.put("shopIncome", s.getShopIncome());
            m.put("status", s.getStatus());
            m.put("statusText", switch (s.getStatus() == null ? 0 : s.getStatus()) {
                case 1 -> "已结算";
                case 2 -> "已冲正";
                default -> "待结算";
            });
            m.put("reverseReason", s.getReverseReason());
            m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().format(TS));
            m.put("settleAt", s.getSettleAt() == null ? null : s.getSettleAt().format(TS));
            rows.add(m);
        }
        // 汇总必须按「整段日期范围」统计，不能只累加当前页（否则换页/换 size 数字就变）
        String where = " FROM settlement WHERE shop_id = ? AND created_at BETWEEN ? AND ? AND status <> 2";
        Object[] args = {shop.getId(), start, end};
        int gmv = intOf("SELECT COALESCE(SUM(goods_amount),0)" + where, args);
        int commission = intOf("SELECT COALESCE(SUM(commission),0)" + where, args);
        int income = intOf("SELECT COALESCE(SUM(shop_income),0)" + where, args);
        int settled = intOf("SELECT COALESCE(SUM(shop_income),0)" + where + " AND status = 1", args);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("gmv", gmv);
        summary.put("commission", commission);
        summary.put("income", income);
        summary.put("settledIncome", settled);
        summary.put("pendingIncome", income - settled);
        summary.put("from", start.toLocalDate().toString());
        summary.put("to", end.toLocalDate().toString());
        return Map.of("summary", summary, "page", new PageResult<>(rows, p.getTotal(), page, size));
    }

    /** 营收统计：今日/本周/本月（只统计已完成订单）。 */
    public Map<String, Object> summary(Long userId) {
        Shop shop = requireShop(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", range(shop.getId(), LocalDate.now().atStartOfDay()));
        m.put("week", range(shop.getId(), LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()));
        m.put("month", range(shop.getId(), LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        m.put("settleFreezeDays", config.getInt("settle.freeze_days", 1));
        return m;
    }

    private Map<String, Object> range(Long shopId, LocalDateTime start) {
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT COUNT(*) AS orders, COALESCE(SUM(goods_amount),0) AS gmv, "
                        + " COALESCE(SUM(commission),0) AS commission, COALESCE(SUM(shop_income),0) AS income "
                        + "FROM orders WHERE shop_id = ? AND status = ? AND created_at >= ?",
                shopId, OrderStatus.FINISHED, start);
        Map<String, Object> m = new LinkedHashMap<>();
        int orders = ((Number) r.get("orders")).intValue();
        m.put("orderCount", orders);
        m.put("gmv", ((Number) r.get("gmv")).intValue());
        m.put("commission", ((Number) r.get("commission")).intValue());
        m.put("income", ((Number) r.get("income")).intValue());
        m.put("avgPrice", orders == 0 ? 0 : ((Number) r.get("gmv")).intValue() / orders);
        return m;
    }

    // ---------------- 提现 ----------------

    @Transactional
    public Long applyWithdraw(Long userId, Integer amount) {
        Shop shop = requireShop(userId);
        int min = config.getInt("settle.min_withdraw", 1000);
        if (amount == null || amount < min) {
            throw new BizException(ErrorCode.PARAM, "最低提现 " + money(min) + " 元");
        }
        Wallet w = walletOf(shop.getId());
        if (w == null || w.getBalance() == null || w.getBalance() < amount) {
            throw new BizException(ErrorCode.PARAM, "可提现余额不足");
        }
        int rows = jdbc.update("UPDATE wallet SET balance = balance - ?, frozen = frozen + ?, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND balance >= ?",
                amount, amount, LocalDateTime.now(), w.getId(), amount);
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM, "余额已变化，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO withdraw (owner_type, owner_id, amount, status, channel, applied_at) VALUES (?,?,?,?,?,?)",
                OWNER_SHOP, shop.getId(), amount, 0, "WXPAY", now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Wallet after = walletMapper.selectById(w.getId());
        flow(after, "WITHDRAW", String.valueOf(id), -1, amount, "提现申请冻结（提现单 " + id + "）");
        return id;
    }

    public List<Map<String, Object>> withdrawals(Long userId) {
        Shop shop = requireShop(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, amount, status, channel, fail_reason, applied_at, audited_at, paid_at "
                        + "FROM withdraw WHERE owner_type = ? AND owner_id = ? ORDER BY id DESC LIMIT 50",
                OWNER_SHOP, shop.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            int status = ((Number) r.get("status")).intValue();
            m.put("id", r.get("id"));
            m.put("amount", r.get("amount"));
            m.put("status", status);
            m.put("statusText", switch (status) {
                case 1 -> "打款中";
                case 2 -> "已到账";
                case 3 -> "已驳回";
                default -> "待审核";
            });
            m.put("channel", r.get("channel"));
            m.put("failReason", r.get("fail_reason"));
            m.put("appliedAt", str(r.get("applied_at")));
            m.put("auditedAt", str(r.get("audited_at")));
            m.put("paidAt", str(r.get("paid_at")));
            rows.add(m);
        }
        return rows;
    }

    // ---------------- 平台侧（后台审核用） ----------------

    public PageResult<Map<String, Object>> adminWithdrawals(Integer status, int page, int size) {
        String where = status == null ? "" : " WHERE w.status = " + status;
        long total = intOf("SELECT COUNT(*) FROM withdraw w" + where);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT w.*, s.name AS shop_name, u.nickname AS rider_name FROM withdraw w "
                        + "LEFT JOIN shop s ON w.owner_type = 2 AND s.id = w.owner_id "
                        + "LEFT JOIN rider r ON w.owner_type = 3 AND r.id = w.owner_id "
                        + "LEFT JOIN `user` u ON u.id = r.user_id"
                        + where + " ORDER BY w.id DESC LIMIT ? OFFSET ?",
                Math.min(size, 50), Math.max(0, (page - 1) * size));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            int st = ((Number) r.get("status")).intValue();
            m.put("id", r.get("id"));
            m.put("ownerType", r.get("owner_type"));
            m.put("ownerId", r.get("owner_id"));
            m.put("shopName", r.get("shop_name"));
            m.put("riderName", r.get("rider_name"));
            m.put("amount", r.get("amount"));
            m.put("status", st);
            m.put("statusText", switch (st) {
                case 1 -> "打款中";
                case 2 -> "已到账";
                case 3 -> "已驳回";
                default -> "待审核";
            });
            m.put("channel", r.get("channel"));
            m.put("failReason", r.get("fail_reason"));
            m.put("appliedAt", str(r.get("applied_at")));
            m.put("auditedAt", str(r.get("audited_at")));
            out.add(m);
        }
        return new PageResult<>(out, total, page, size);
    }

    /**
     * 审核提现：通过=提交打款（mock 下直接视为已到账），驳回=解冻退回可用余额。
     *
     * <p>真实通道下这里会真的调微信「商家转账」；受理成功置为 1 打款中，
     * 到账确认后由回调/人工置 2 已到账。
     */
    @Transactional
    public void auditWithdraw(Long adminId, Long withdrawId, boolean pass, String reason) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT * FROM withdraw WHERE id = ?", withdrawId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_FOUND, "提现单不存在");
        }
        int status = ((Number) row.get("status")).intValue();
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS, "该提现单已处理");
        }
        int amount = ((Number) row.get("amount")).intValue();
        int ownerType = ((Number) row.get("owner_type")).intValue();
        long ownerId = ((Number) row.get("owner_id")).longValue();
        Wallet w = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, ownerType).eq(Wallet::getOwnerId, ownerId));
        if (w == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "钱包不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (pass) {
            // 先打款，成功了才动账；失败抛异常回滚，冻结原样保留供重试
            int amountFen = amount;
            PayoutGateway.PayoutResult pr = payoutGateway.transfer(
                    "W" + withdrawId, resolveOpenid(ownerType, ownerId), amountFen,
                    "结算打款（提现单 " + withdrawId + "）", "1000");
            if (!pr.success()) {
                throw new BizException(ErrorCode.PAY_FAILED, "打款失败：" + pr.message());
            }
            jdbc.update("UPDATE wallet SET frozen = GREATEST(frozen - ?, 0), updated_at = ? WHERE id = ?",
                    amount, now, w.getId());
            // mock 打款即时到账；真实通道受理后是「打款中」，等结果回填
            int newStatus = payoutGateway.mock() ? 2 : 1;
            LocalDateTime paidAt = newStatus == 2 ? now : null;
            jdbc.update("UPDATE withdraw SET status = ?, audited_at = ?, paid_at = ?, fail_reason = NULL WHERE id = ?",
                    newStatus, now, paidAt, withdrawId);
            // wallet_flow 有 uk_biz(biz_type,biz_no) 唯一约束：申请时已用 WITHDRAW+id，
            // 打款这一步必须换 biz_type，否则撞唯一键
            flow(walletMapper.selectById(w.getId()), "WITHDRAW_PAY", String.valueOf(withdrawId), -1, amount,
                    "提现打款（提现单 " + withdrawId + "）：" + pr.message());
        } else {
            jdbc.update("UPDATE wallet SET frozen = GREATEST(frozen - ?, 0), balance = balance + ?, "
                    + "version = version + 1, updated_at = ? WHERE id = ?", amount, amount, now, w.getId());
            jdbc.update("UPDATE withdraw SET status = 3, audited_at = ?, fail_reason = ? WHERE id = ?",
                    now, reason == null ? "平台驳回" : reason, withdrawId);
            Wallet after = walletMapper.selectById(w.getId());
            flow(after, "WITHDRAW_REFUND", String.valueOf(withdrawId), 1, amount, "提现驳回退回（" + reason + "）");
        }
    }

    /**
     * 处理转账结果通知（提现打款异步回执）。
     * out_bill_no = "W" + withdrawId，与 auditWithdraw 提交时一致。
     */
    @Transactional
    public void handleTransferNotify(String outBillNo, boolean success, String message) {
        if (outBillNo == null || !outBillNo.startsWith("W")) {
            return;
        }
        long withdrawId;
        try {
            withdrawId = Long.parseLong(outBillNo.substring(1));
        } catch (NumberFormatException e) {
            return;
        }
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT * FROM withdraw WHERE id = ?", withdrawId);
        } catch (Exception e) {
            return;
        }
        int status = ((Number) row.get("status")).intValue();
        // 幂等：已终态直接返回，微信会重复推送
        if (status == 2 || status == 3) {
            return;
        }
        int amount = ((Number) row.get("amount")).intValue();
        int ownerType = ((Number) row.get("owner_type")).intValue();
        long ownerId = ((Number) row.get("owner_id")).longValue();
        LocalDateTime now = LocalDateTime.now();
        Wallet w = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, ownerType).eq(Wallet::getOwnerId, ownerId));
        if (success) {
            jdbc.update("UPDATE withdraw SET status = 2, paid_at = ?, fail_reason = NULL WHERE id = ?",
                    now, withdrawId);
        } else {
            // 打款失败：冻结解冻退回可用余额
            jdbc.update("UPDATE wallet SET frozen = GREATEST(frozen - ?, 0), balance = balance + ?, "
                    + "version = version + 1, updated_at = ? WHERE id = ?", amount, amount, now, w.getId());
            jdbc.update("UPDATE withdraw SET status = 3, fail_reason = ? WHERE id = ?",
                    message == null ? "打款失败" : message, withdrawId);
            flow(walletMapper.selectById(w.getId()), "WITHDRAW_REFUND",
                    withdrawId + "-FAIL", 1, amount, "打款失败退回（提现单 " + withdrawId + "）");
        }
    }

    /**
     * 找到收款人的小程序 openid。
     * 商家：shop.owner_user_id → user.openid；骑手：rider.user_id → user.openid。
     * 找不到说明对方还没用这个小程序登录过，微信无法转账。
     */
    private String resolveOpenid(int ownerType, long ownerId) {
        try {
            String sql = ownerType == OWNER_SHOP
                    ? "SELECT u.openid FROM shop s JOIN `user` u ON u.id = s.owner_user_id WHERE s.id = ?"
                    : "SELECT u.openid FROM rider r JOIN `user` u ON u.id = r.user_id WHERE r.id = ?";
            return jdbc.queryForObject(sql, String.class, ownerId);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 内部 ----------------

    private Shop requireShop(Long userId) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (shop == null || shop.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有店铺");
        }
        return shop;
    }

    private Wallet walletOf(Long shopId) {
        return walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, OWNER_SHOP).eq(Wallet::getOwnerId, shopId));
    }

    private void flow(Wallet wallet, String bizType, String bizNo, int direction, int amount, String remark) {
        WalletFlow f = new WalletFlow();
        f.setWalletId(wallet.getId());
        f.setBizType(bizType);
        f.setBizNo(bizNo);
        f.setDirection(direction);
        f.setAmount(amount);
        f.setBalanceAfter(wallet.getBalance() == null ? 0 : wallet.getBalance());
        f.setRemark(remark);
        f.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(f);
    }

    private int intOf(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(TS);
        }
        return String.valueOf(v).replace('T', ' ');
    }

    private String money(int fen) {
        return String.format("%.2f", fen / 100.0);
    }
}
