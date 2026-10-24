package com.lcu.delivery.modules.coupon;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.coupon.entity.Coupon;
import com.lcu.delivery.modules.coupon.entity.UserCoupon;
import com.lcu.delivery.modules.coupon.mapper.CouponMapper;
import com.lcu.delivery.modules.coupon.mapper.UserCouponMapper;
import com.lcu.delivery.modules.notify.NotifyService;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 优惠券：领券、我的券、算价抵扣、下单核销、退款返还。
 *
 * 金额全部是「分」。折扣券的 amount 存千分比（880 = 8.8 折，实付 88%）。
 * 优惠只作用在商品金额上，不抵打包费和配送费。
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TYPE_FULL_CUT = 1;
    private static final int TYPE_DISCOUNT = 2;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;
    private final ShopMapper shopMapper;

    public CouponService(CouponMapper couponMapper, UserCouponMapper userCouponMapper, JdbcTemplate jdbc,
                         NotifyService notifyService, ShopMapper shopMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.jdbc = jdbc;
        this.notifyService = notifyService;
        this.shopMapper = shopMapper;
    }

    /** 一张已经校验通过、可以立刻抵扣的券。 */
    public record Applied(Long userCouponId, Long couponId, String name, int type, int threshold, int discount) {
    }

    // ---------------- 用户端 ----------------

    /** 领券中心：还能领的券（全场 + 当前店铺），已领过的不再出现。 */
    public List<Map<String, Object>> available(Long userId, Long shopId) {
        LocalDateTime now = LocalDateTime.now();
        var qw = Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getStartAt, now)
                .ge(Coupon::getEndAt, now)
                .gt(Coupon::getRemain, 0)
                .orderByAsc(Coupon::getThreshold);
        if (shopId != null) {
            qw.and(w -> w.eq(Coupon::getScope, 1).or().eq(Coupon::getShopId, shopId));
        } else {
            qw.eq(Coupon::getScope, 1);
        }
        List<Coupon> list = couponMapper.selectList(qw);

        Set<Long> claimed = new LinkedHashSet<>();
        for (UserCoupon uc : userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId))) {
            claimed.add(uc.getCouponId());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Coupon c : list) {
            if (claimed.contains(c.getId())) {
                continue;
            }
            rows.add(toVo(c, null, null, 0));
        }
        return rows;
    }

    /** 领券：库存用条件更新扣减，防超发；同一张券每人限领一张。 */
    @Transactional
    public void claim(Long userId, Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getStartAt() != null && now.isBefore(c.getStartAt())) {
            throw new BizException(ErrorCode.PARAM, "优惠券还没开始发放");
        }
        if (c.getEndAt() != null && now.isAfter(c.getEndAt())) {
            throw new BizException(ErrorCode.PARAM, "优惠券已过期");
        }
        Long got = userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (got != null && got > 0) {
            throw new BizException(ErrorCode.PARAM, "这张券你已经领过了");
        }
        int rows = jdbc.update("UPDATE coupon SET remain = remain - 1 WHERE id = ? AND remain > 0 AND status = 1", couponId);
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM, "手慢了，券已被领完");
        }
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(0);
        uc.setGotAt(now);
        userCouponMapper.insert(uc);
        try {
            notifyService.notifyCoupon(userId, c.getName(),
                    c.getEndAt() == null ? "" : c.getEndAt().format(TS));
        } catch (Exception e) {
            log.debug("领券订阅消息跳过：{}", e.getMessage());
        }
    }

    /**
     * 我的券。status 传 null 返回全部；shopId + amount 用来算「这单能不能用」，
     * 前端购物车直接用 usable 排序即可。
     */
    public List<Map<String, Object>> mine(Long userId, Integer status, Long shopId, Integer amount) {
        List<UserCoupon> list = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Coupon> templates = new LinkedHashMap<>();
        List<Long> ids = list.stream().map(UserCoupon::getCouponId).distinct().toList();
        for (Coupon c : couponMapper.selectBatchIds(ids)) {
            templates.put(c.getId(), c);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserCoupon uc : list) {
            Coupon c = templates.get(uc.getCouponId());
            if (c == null) {
                continue;
            }
            boolean expired = c.getEndAt() != null && now.isAfter(c.getEndAt());
            int effective = uc.getStatus() != null && uc.getStatus() == 1 ? 1 : (expired ? 2 : 0);
            if (status != null && effective != status) {
                continue;
            }
            Map<String, Object> vo = toVo(c, uc, shopId, amount == null ? 0 : amount);
            vo.put("status", effective);
            vo.put("statusText", switch (effective) {
                case 1 -> "已使用";
                case 2 -> "已过期";
                default -> "未使用";
            });
            vo.put("nearExpire", c.getEndAt() != null && !expired && c.getEndAt().isBefore(now.plusDays(3)));
            rows.add(vo);
        }
        return rows;
    }

    /** 下单/算价时校验券是否可用，返回抵扣金额。不可用直接抛业务异常。 */
    public Applied apply(Long userId, Long userCouponId, Long shopId, int goodsAmount) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !userId.equals(uc.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (uc.getStatus() == null || uc.getStatus() != 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "这张券已使用或已失效");
        }
        Coupon c = couponMapper.selectById(uc.getCouponId());
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM, "优惠券已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getStartAt() != null && now.isBefore(c.getStartAt())) {
            throw new BizException(ErrorCode.PARAM, "优惠券还没到可用的时间");
        }
        if (c.getEndAt() != null && now.isAfter(c.getEndAt())) {
            throw new BizException(ErrorCode.PARAM, "优惠券已过期");
        }
        if (c.getScope() != null && c.getScope() == 2 && c.getShopId() != null && !c.getShopId().equals(shopId)) {
            throw new BizException(ErrorCode.PARAM, "这张券仅限指定店铺使用");
        }
        int threshold = c.getThreshold() == null ? 0 : c.getThreshold();
        if (goodsAmount < threshold) {
            throw new BizException(ErrorCode.PARAM,
                    "还差 " + fen(threshold - goodsAmount) + " 元才可用这张券");
        }
        return new Applied(uc.getId(), c.getId(), c.getName(),
                c.getType() == null ? TYPE_FULL_CUT : c.getType(), threshold, discountOf(c, goodsAmount));
    }

    /** 核销：条件更新，重复提交不会二次用券（并发下只有一个能成功）。 */
    public void consume(Long userCouponId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getUsedOrderId, orderId)
                .set(UserCoupon::getUsedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "优惠券已被使用，请刷新后重试");
        }
    }

    /** 取消 / 退款时把券退回用户手里。 */
    public void restoreByOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                .eq(UserCoupon::getUsedOrderId, orderId)
                .eq(UserCoupon::getStatus, 1)
                .set(UserCoupon::getStatus, 0)
                .set(UserCoupon::getUsedOrderId, null)
                .set(UserCoupon::getUsedAt, null));
    }

    /** 满减：直接减；折扣：按千分比折算。都不能超过商品金额。 */
    public static int discountOf(Coupon c, int goodsAmount) {
        int amount = c.getAmount() == null ? 0 : c.getAmount();
        int discount;
        if (c.getType() != null && c.getType() == TYPE_DISCOUNT) {
            int rate = Math.max(0, Math.min(1000, amount));
            discount = (int) Math.round(goodsAmount * (1000 - rate) / 1000.0);
        } else {
            discount = amount;
        }
        return Math.max(0, Math.min(discount, goodsAmount));
    }

    // ---------------- 管理端 ----------------

    public Coupon create(String name, int type, int threshold, int amount, int total,
                         int scope, Long shopId, LocalDateTime startAt, LocalDateTime endAt) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "券名称不能为空");
        }
        if (type != TYPE_FULL_CUT && type != TYPE_DISCOUNT) {
            throw new BizException(ErrorCode.PARAM, "券类型只能是满减或折扣");
        }
        if (type == TYPE_DISCOUNT && (amount < 1 || amount > 999)) {
            throw new BizException(ErrorCode.PARAM, "折扣千分比应在 1~999 之间（如 880 = 8.8 折）");
        }
        if (type == TYPE_FULL_CUT && amount <= 0) {
            throw new BizException(ErrorCode.PARAM, "减免金额必须大于 0");
        }
        if (total <= 0) {
            throw new BizException(ErrorCode.PARAM, "发放数量必须大于 0");
        }
        if (scope == 2 && shopId == null) {
            throw new BizException(ErrorCode.PARAM, "指定店铺券必须选择店铺");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BizException(ErrorCode.PARAM, "结束时间必须晚于开始时间");
        }
        Coupon c = new Coupon();
        c.setName(name);
        c.setType(type);
        c.setThreshold(Math.max(0, threshold));
        c.setAmount(amount);
        c.setTotal(total);
        c.setRemain(total);
        c.setScope(scope);
        c.setShopId(scope == 2 ? shopId : null);
        c.setStartAt(startAt);
        c.setEndAt(endAt);
        c.setStatus(1);
        c.setCreatedAt(LocalDateTime.now());
        couponMapper.insert(c);
        return c;
    }

    // ---------------- 商家自建店铺券 ----------------

    /** 商家只能看到/管理自己店铺的券（scope=2 且 shop_id 是自己的店） */
    public List<Map<String, Object>> merchantList(Long userId) {
        Shop shop = requireShop(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Coupon c : couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getScope, 2)
                .eq(Coupon::getShopId, shop.getId())
                .orderByDesc(Coupon::getId))) {
            Map<String, Object> m = toVo(c, null, shop.getId(), 0);
            m.put("claimed", intOf("SELECT COUNT(*) FROM user_coupon WHERE coupon_id = ?", c.getId()));
            m.put("used", intOf("SELECT COUNT(*) FROM user_coupon WHERE coupon_id = ? AND status = 1", c.getId()));
            m.put("status", c.getStatus());
            rows.add(m);
        }
        return rows;
    }

    @Transactional
    public Long merchantCreate(Long userId, String name, int type, int threshold, int amount,
                               int total, LocalDateTime startAt, LocalDateTime endAt) {
        Shop shop = requireShop(userId);
        Coupon c = create(name, type, threshold, amount, total, 2, shop.getId(), startAt, endAt);
        return c.getId();
    }

    public void merchantToggle(Long userId, Long couponId, int status) {
        Shop shop = requireShop(userId);
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || !shop.getId().equals(c.getShopId()) || c.getScope() == null || c.getScope() != 2) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        c.setStatus(status);
        couponMapper.updateById(c);
    }

    private Shop requireShop(Long userId) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (shop == null || shop.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有店铺");
        }
        if (shop.getAuditStatus() == null || shop.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "店铺通过审核后才能发券");
        }
        return shop;
    }

    private int intOf(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    public PageResult<Map<String, Object>> adminList(int page, int size) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT c.*, "
                        + " (SELECT COUNT(*) FROM user_coupon u WHERE u.coupon_id = c.id) AS claimed, "
                        + " (SELECT COUNT(*) FROM user_coupon u WHERE u.coupon_id = c.id AND u.status = 1) AS used "
                        + "FROM coupon c ORDER BY c.id DESC")) {
            Map<String, Object> m = new LinkedHashMap<>();
            int type = num(r, "type");
            int scope = num(r, "scope");
            m.put("id", num(r, "id"));
            m.put("name", r.get("name"));
            m.put("type", type);
            m.put("typeText", type == TYPE_DISCOUNT ? "折扣" : "满减");
            m.put("threshold", num(r, "threshold"));
            m.put("amount", num(r, "amount"));
            m.put("total", num(r, "total"));
            m.put("remain", num(r, "remain"));
            m.put("scope", scope);
            m.put("scopeText", scope == 2 ? "指定店铺" : "全场");
            m.put("shopId", r.get("shop_id") == null ? null : num(r, "shop_id"));
            m.put("startAt", time(r.get("start_at")));
            m.put("endAt", time(r.get("end_at")));
            m.put("status", num(r, "status"));
            m.put("claimed", num(r, "claimed"));
            m.put("used", num(r, "used"));
            m.put("desc", descOf(type, num(r, "threshold"), num(r, "amount")));
            all.add(m);
        }
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<Map<String, Object>> rows = from >= all.size() ? new ArrayList<>() : all.subList(from, to);
        return new PageResult<>(rows, all.size(), page, size);
    }

    public void updateStatus(Long id, int status) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        c.setStatus(status);
        couponMapper.updateById(c);
    }

    // ---------------- 内部 ----------------

    private Map<String, Object> toVo(Coupon c, UserCoupon uc, Long shopId, int amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("userCouponId", uc == null ? null : uc.getId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("threshold", c.getThreshold());
        m.put("amount", c.getAmount());
        m.put("desc", desc(c));
        m.put("scope", c.getScope());
        m.put("shopId", c.getShopId());
        m.put("remain", c.getRemain());
        m.put("startAt", c.getStartAt() == null ? null : c.getStartAt().format(TS));
        m.put("endAt", c.getEndAt() == null ? null : c.getEndAt().format(TS));
        if (uc != null) {
            m.put("gotAt", uc.getGotAt() == null ? null : uc.getGotAt().format(TS));
        }
        boolean expired = c.getEndAt() != null && LocalDateTime.now().isAfter(c.getEndAt());
        boolean scopeOk = c.getScope() == null || c.getScope() == 1
                || (shopId != null && shopId.equals(c.getShopId()));
        int threshold = c.getThreshold() == null ? 0 : c.getThreshold();
        boolean usable = uc != null && uc.getStatus() != null && uc.getStatus() == 0
                && !expired && scopeOk && amount >= threshold;
        if (uc != null) {
            m.put("usable", usable);
            m.put("discount", usable ? discountOf(c, amount) : 0);
            m.put("reason", usable ? null
                    : expired ? "已过期"
                    : !scopeOk ? "仅限指定店铺"
                    : amount < threshold ? "还差 " + fen(threshold - amount) + " 元"
                    : "不可用");
        }
        return m;
    }

    private String desc(Coupon c) {
        String th = c.getThreshold() == null || c.getThreshold() == 0
                ? "无门槛" : "满 " + fen(c.getThreshold()) + " 元";
        if (c.getType() != null && c.getType() == TYPE_DISCOUNT) {
            return th + "打 " + (c.getAmount() / 100.0) + " 折";
        }
        return th + "减 " + fen(c.getAmount()) + " 元";
    }

    private static int num(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? 0 : ((Number) v).intValue();
    }

    private static String time(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(TS);
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(TS);
        }
        return String.valueOf(v);
    }

    private static String descOf(int type, int threshold, int amount) {
        String th = threshold == 0 ? "无门槛" : String.format("满 %.2f 元", threshold / 100.0);
        return type == TYPE_DISCOUNT
                ? th + "打 " + (amount / 100.0) + " 折"
                : th + "减 " + String.format("%.2f", amount / 100.0) + " 元";
    }

    private static String fen(Integer amount) {
        return String.format("%.2f", (amount == null ? 0 : amount) / 100.0);
    }
}
