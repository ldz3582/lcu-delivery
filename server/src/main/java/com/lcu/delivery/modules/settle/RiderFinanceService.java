package com.lcu.delivery.modules.settle;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.settle.entity.RiderPenalty;
import com.lcu.delivery.modules.settle.entity.Settlement;
import com.lcu.delivery.modules.settle.entity.Wallet;
import com.lcu.delivery.modules.settle.entity.WalletFlow;
import com.lcu.delivery.modules.settle.mapper.RiderPenaltyMapper;
import com.lcu.delivery.modules.settle.mapper.SettlementMapper;
import com.lcu.delivery.modules.settle.mapper.WalletFlowMapper;
import com.lcu.delivery.modules.settle.mapper.WalletMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 骑手资金：佣金明细、钱包余额、提现、账单、处罚记录。
 * 与商家钱包共用 wallet / wallet_flow / withdraw / settlement 表，靠 owner_type=3 区分。
 */
@Service
public class RiderFinanceService {

    private static final int OWNER_RIDER = 3;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WalletMapper walletMapper;
    private final WalletFlowMapper flowMapper;
    private final SettlementMapper settlementMapper;
    private final RiderPenaltyMapper penaltyMapper;
    private final RiderMapper riderMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService config;

    public RiderFinanceService(WalletMapper walletMapper, WalletFlowMapper flowMapper,
                               SettlementMapper settlementMapper, RiderPenaltyMapper penaltyMapper,
                               RiderMapper riderMapper, OrderMapper orderMapper,
                               JdbcTemplate jdbc, SysConfigService config) {
        this.walletMapper = walletMapper;
        this.flowMapper = flowMapper;
        this.settlementMapper = settlementMapper;
        this.penaltyMapper = penaltyMapper;
        this.riderMapper = riderMapper;
        this.orderMapper = orderMapper;
        this.jdbc = jdbc;
        this.config = config;
    }

    // ---------------- 钱包 ----------------

    public Map<String, Object> wallet(Long userId) {
        Rider rider = requireRider(userId);
        Wallet w = walletOf(rider.getId());
        int walletId = w == null ? 0 : w.getId().intValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riderId", rider.getId());
        m.put("balance", w == null ? 0 : w.getBalance());
        m.put("frozen", w == null ? 0 : w.getFrozen());
        m.put("totalIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'RIDER_SETTLE'", walletId));
        m.put("todayIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'RIDER_SETTLE' AND created_at >= ?",
                walletId, LocalDate.now().atStartOfDay()));
        m.put("monthIncome", walletId == 0 ? 0 : intOf(
                "SELECT COALESCE(SUM(amount),0) FROM wallet_flow WHERE wallet_id = ? AND direction = 1 AND biz_type = 'RIDER_SETTLE' AND created_at >= ?",
                walletId, LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        m.put("totalPenalty", intOf(
                "SELECT COALESCE(SUM(amount),0) FROM rider_penalty WHERE rider_id = ?", rider.getId()));
        m.put("minWithdraw", config.getInt("settle.min_withdraw", 1000));
        m.put("score", rider.getScore());
        m.put("totalOrders", rider.getTotalOrders());
        m.put("timeoutOrders", rider.getTimeoutOrders());

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

    // ---------------- 佣金明细 / 账单 ----------------

    public Map<String, Object> settlements(Long userId, int page, int size) {
        Rider rider = requireRider(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Settlement> list = settlementMapper.selectList(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getRiderId, rider.getId())
                .orderByDesc(Settlement::getId)
                .last("limit " + Math.min(size, 50) + " offset " + Math.max(0, (page - 1) * size)));
        int income = 0;
        int pending = 0;
        for (Settlement s : list) {
            Order o = s.getOrderId() == null ? null : orderMapper.selectById(s.getOrderId());
            int riderIncome = s.getRiderIncome() == null ? 0 : s.getRiderIncome();
            income += riderIncome;
            if (s.getStatus() != null && s.getStatus() == 0) {
                pending += riderIncome;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("orderId", s.getOrderId());
            m.put("orderNo", o == null ? null : o.getOrderNo());
            m.put("goodsAmount", s.getGoodsAmount());
            m.put("riderIncome", riderIncome);
            m.put("status", s.getStatus());
            m.put("statusText", s.getStatus() != null && s.getStatus() == 1 ? "已入账" : "待结算");
            m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().format(TS));
            m.put("settleAt", s.getSettleAt() == null ? null : s.getSettleAt().format(TS));
            rows.add(m);
        }
        long total = settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getRiderId, rider.getId()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderCount", total);
        summary.put("pageIncome", income);
        summary.put("pendingIncome", pending);
        return Map.of("summary", summary, "list", rows, "total", total, "page", page, "size", size);
    }

    public Map<String, Object> summary(Long userId) {
        Rider rider = requireRider(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", range(rider.getId(), LocalDate.now().atStartOfDay()));
        m.put("week", range(rider.getId(), LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()));
        m.put("month", range(rider.getId(), LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        m.put("settleFreezeDays", config.getInt("settle.freeze_days", 1));
        return m;
    }

    private Map<String, Object> range(Long riderId, LocalDateTime start) {
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT COUNT(*) AS orders, COALESCE(SUM(rider_income),0) AS income "
                        + "FROM orders WHERE rider_id = ? AND status IN (50,70,80) AND created_at >= ?",
                riderId, start);
        int orders = ((Number) r.get("orders")).intValue();
        int income = ((Number) r.get("income")).intValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", orders);
        m.put("income", income);
        m.put("avgIncome", orders == 0 ? 0 : income / orders);
        return m;
    }

    // ---------------- 提现 ----------------

    @Transactional
    public Long applyWithdraw(Long userId, Integer amount) {
        Rider rider = requireRider(userId);
        int min = config.getInt("settle.min_withdraw", 1000);
        if (amount == null || amount < min) {
            throw new BizException(ErrorCode.PARAM, "最低提现 " + money(min) + " 元");
        }
        Wallet w = walletOf(rider.getId());
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
                OWNER_RIDER, rider.getId(), amount, 0, "WXPAY", now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Wallet after = walletMapper.selectById(w.getId());
        flow(after, "WITHDRAW", String.valueOf(id), -1, amount, "提现申请冻结（提现单 " + id + "）");
        return id;
    }

    public List<Map<String, Object>> withdrawals(Long userId) {
        Rider rider = requireRider(userId);
        return withdrawRows("WHERE owner_type = ? AND owner_id = ? ORDER BY id DESC LIMIT 50", OWNER_RIDER, rider.getId());
    }

    // ---------------- 处罚记录 ----------------

    public List<Map<String, Object>> penalties(Long userId) {
        Rider rider = requireRider(userId);
        return penaltyRows(penaltyMapper.selectList(Wrappers.<RiderPenalty>lambdaQuery()
                .eq(RiderPenalty::getRiderId, rider.getId())
                .orderByDesc(RiderPenalty::getId)
                .last("limit 50")));
    }

    /** 生成罚单并从钱包扣款（余额不足时扣到 0 为止，流水照记，方便对账）。 */
    @Transactional
    public Long addPenalty(Long riderId, Long orderId, int type, int amount, String remark) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM, "扣罚金额必须大于 0");
        }
        RiderPenalty p = new RiderPenalty();
        p.setRiderId(riderId);
        p.setOrderId(orderId);
        p.setType(type);
        p.setAmount(amount);
        p.setRemark(remark);
        p.setCreatedAt(LocalDateTime.now());
        penaltyMapper.insert(p);

        Wallet w = walletOf(riderId);
        if (w == null) {
            w = new Wallet();
            w.setOwnerType(OWNER_RIDER);
            w.setOwnerId(riderId);
            w.setBalance(0);
            w.setFrozen(0);
            w.setVersion(0);
            w.setUpdatedAt(LocalDateTime.now());
            walletMapper.insert(w);
        }
        int deduct = Math.min(amount, w.getBalance() == null ? 0 : w.getBalance());
        if (deduct > 0) {
            jdbc.update("UPDATE wallet SET balance = balance - ?, version = version + 1, updated_at = ? WHERE id = ?",
                    deduct, LocalDateTime.now(), w.getId());
        }
        Wallet after = walletMapper.selectById(w.getId());
        flow(after, "PENALTY", String.valueOf(p.getId()), -1, deduct > 0 ? deduct : 0,
                "处罚扣款（" + penaltyTypeText(type) + "）" + (remark == null ? "" : "：" + remark));
        return p.getId();
    }

    public List<Map<String, Object>> allPenalties(Long riderId) {
        return penaltyRows(penaltyMapper.selectList(Wrappers.<RiderPenalty>lambdaQuery()
                .eq(riderId != null, RiderPenalty::getRiderId, riderId)
                .orderByDesc(RiderPenalty::getId)
                .last("limit 100")));
    }

    // ---------------- 内部 ----------------

    private Rider requireRider(Long userId) {
        Rider rider = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        if (rider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还不是骑手，请先入驻");
        }
        return rider;
    }

    private Wallet walletOf(Long riderId) {
        return walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, OWNER_RIDER).eq(Wallet::getOwnerId, riderId));
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

    private List<Map<String, Object>> withdrawRows(String where, Object... args) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, amount, status, channel, fail_reason, applied_at, audited_at, paid_at FROM withdraw "
                        + where, args)) {
            Map<String, Object> m = new LinkedHashMap<>();
            int st = ((Number) r.get("status")).intValue();
            m.put("id", r.get("id"));
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
            rows.add(m);
        }
        return rows;
    }

    private List<Map<String, Object>> penaltyRows(List<RiderPenalty> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RiderPenalty p : list) {
            Order o = p.getOrderId() == null ? null : orderMapper.selectById(p.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("riderId", p.getRiderId());
            m.put("orderId", p.getOrderId());
            m.put("orderNo", o == null ? null : o.getOrderNo());
            m.put("type", p.getType());
            m.put("typeText", penaltyTypeText(p.getType()));
            m.put("amount", p.getAmount());
            m.put("remark", p.getRemark());
            m.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().format(TS));
            rows.add(m);
        }
        return rows;
    }

    private String penaltyTypeText(Integer type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case 1 -> "超时";
            case 2 -> "差评";
            default -> "违规操作";
        };
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

    private int intOf(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    private String money(int fen) {
        return String.format("%.2f", fen / 100.0);
    }
}
