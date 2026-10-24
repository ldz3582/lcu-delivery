package com.lcu.delivery.modules.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.AuthUser;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.JwtService;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.admin.entity.AdminUser;
import com.lcu.delivery.modules.admin.mapper.AdminUserMapper;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.fence.FenceService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.settle.SettlementService;
import com.lcu.delivery.modules.settle.entity.Settlement;
import com.lcu.delivery.modules.settle.mapper.SettlementMapper;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final AdminUserMapper adminMapper;
    private final ShopMapper shopMapper;
    private final RiderMapper riderMapper;
    private final OrderMapper orderMapper;
    private final SettlementMapper settlementMapper;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final FenceService fenceService;
    private final SettlementService settlementService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminService(AdminUserMapper adminMapper, ShopMapper shopMapper, RiderMapper riderMapper,
                        OrderMapper orderMapper, SettlementMapper settlementMapper, JwtService jwtService,
                        JdbcTemplate jdbc, FenceService fenceService, SettlementService settlementService) {
        this.settlementService = settlementService;
        this.adminMapper = adminMapper;
        this.shopMapper = shopMapper;
        this.riderMapper = riderMapper;
        this.orderMapper = orderMapper;
        this.settlementMapper = settlementMapper;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.fenceService = fenceService;
    }

    public record LoginResp(String token, String username, String realName, String role) {
    }

    public LoginResp login(String username, String password) {
        AdminUser admin = adminMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !encoder.matches(password, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        admin.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(admin);
        String token = jwtService.issue(new AuthUser(admin.getId(), "ADMIN"));
        return new LoginResp(token, admin.getUsername(), admin.getRealName(), admin.getRole());
    }

    public Map<String, Object> overview() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayOrders", jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE created_at >= ?", Long.class, today));
        m.put("todayGmv", jdbc.queryForObject(
                "SELECT COALESCE(SUM(pay_amount),0) FROM orders WHERE paid_at >= ? AND status >= 10", Long.class, today));
        m.put("totalShops", jdbc.queryForObject("SELECT COUNT(*) FROM shop WHERE deleted_at IS NULL", Long.class));
        m.put("pendingShops", jdbc.queryForObject("SELECT COUNT(*) FROM shop WHERE audit_status = 0", Long.class));
        m.put("totalRiders", jdbc.queryForObject("SELECT COUNT(*) FROM rider", Long.class));
        m.put("pendingRiders", jdbc.queryForObject("SELECT COUNT(*) FROM rider WHERE audit_status = 0", Long.class));
        m.put("totalUsers", jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE deleted_at IS NULL", Long.class));
        m.put("inDelivery", jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status IN (30,40)", Long.class));
        return m;
    }


    /** 用户列表：注册用户 → 管理端可见（含核验状态与下单数） */
    public PageResult<Map<String, Object>> users(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int sz = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE u.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.nickname LIKE ? OR u.openid LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM `user` u" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(sz);
        pageArgs.add((p - 1) * sz);
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT u.id, u.openid, u.nickname, u.role, u.phone_mask, u.identity_status, u.created_at, "
                        + " (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id) AS order_count "
                        + "FROM `user` u" + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("openid", rs.getString("openid"));
                    m.put("nickname", rs.getString("nickname"));
                    m.put("role", rs.getString("role"));
                    m.put("phoneMask", rs.getString("phone_mask"));
                    m.put("identityStatus", rs.getInt("identity_status"));
                    m.put("orderCount", rs.getLong("order_count"));
                    m.put("createdAt", rs.getTimestamp("created_at") == null ? null
                            : rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return m;
                }, pageArgs.toArray());
        return new PageResult<>(rows, total, p, sz);
    }

    public PageResult<Map<String, Object>> shops(Integer auditStatus, int page, int size) {
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Shop>lambdaQuery()
                        .eq(auditStatus != null, Shop::getAuditStatus, auditStatus)
                        .isNull(Shop::getDeletedAt)
                        .orderByAsc(Shop::getAuditStatus)
                        .orderByDesc(Shop::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Shop s : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("campus", s.getCampus());
            m.put("address", s.getAddress());
            m.put("contactPhone", s.getContactPhone());
            m.put("auditStatus", s.getAuditStatus());
            m.put("bizStatus", s.getBizStatus());
            m.put("licenseUrl", s.getLicenseUrl());
            m.put("permitUrl", s.getPermitUrl());
            m.put("photos", parseList(s.getPhotos()));
            m.put("rejectReason", s.getRejectReason());
            m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            m.put("commissionRate", s.getCommissionRate());
            rows.add(m);
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                    });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Transactional
    public void auditShop(Long shopId, boolean pass, String reason) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "店铺不存在");
        }
        int status = pass ? 1 : 2;
        String reject = pass ? null
                : (reason == null || reason.isBlank() ? "资料不齐，请补充后重新提交" : reason);
        shop.setAuditStatus(status);
        shop.setRejectReason(reject);
        shop.setUpdatedAt(LocalDateTime.now());
        // 注意：updateById 会跳过 null 字段，通过时必须用 wrapper 显式把 reject_reason 清空
        shopMapper.update(null, Wrappers.<Shop>lambdaUpdate()
                .eq(Shop::getId, shopId)
                .set(Shop::getAuditStatus, status)
                .set(Shop::getRejectReason, reject)
                .set(Shop::getUpdatedAt, LocalDateTime.now()));
    }

    public PageResult<Map<String, Object>> riders(Integer auditStatus, int page, int size) {
        Page<Rider> p = riderMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Rider>lambdaQuery()
                        .eq(auditStatus != null, Rider::getAuditStatus, auditStatus)
                        .orderByAsc(Rider::getAuditStatus)
                        .orderByDesc(Rider::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Rider r : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("phoneMask", r.getPhoneMask());
            m.put("auditStatus", r.getAuditStatus());
            m.put("workStatus", r.getWorkStatus());
            m.put("score", r.getScore());
            m.put("totalOrders", r.getTotalOrders());
            rows.add(m);
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    @Transactional
    public void auditRider(Long riderId, boolean pass) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "骑手不存在");
        }
        rider.setAuditStatus(pass ? 1 : 2);
        rider.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateById(rider);
    }

    public PageResult<Map<String, Object>> orders(Integer status, String keyword, int page, int size) {
        Page<Order> p = orderMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Order>lambdaQuery()
                        .eq(status != null, Order::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), Order::getOrderNo, keyword)
                        .orderByDesc(Order::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("userId", o.getUserId());
            m.put("shopId", o.getShopId());
            m.put("riderId", o.getRiderId());
            m.put("status", o.getStatus());
            m.put("statusText", OrderStatus.text(o.getStatus()));
            m.put("payAmount", o.getPayAmount());
            m.put("commission", o.getCommission());
            m.put("createdAt", o.getCreatedAt());
            rows.add(m);
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    /** 手动结算一单：演示/财务应急用（正常由 SettleJob 按 T+N 自动跑）。 */
    @Transactional
    public void settleOne(Long settlementId) {
        Settlement s = settlementMapper.selectById(settlementId);
        if (s == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "结算单不存在");
        }
        if (s.getStatus() != null && s.getStatus() == 1) {
            throw new BizException(ErrorCode.ORDER_STATUS, "这单已经结算过了");
        }
        settlementService.settle(s);
    }

    public PageResult<Settlement> settlements(int page, int size) {
        Page<Settlement> p = settlementMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Settlement>lambdaQuery().orderByDesc(Settlement::getId));
        return new PageResult<>(p.getRecords(), p.getTotal(), page, size);
    }

    public List<Map<String, Object>> fences() {
        return jdbc.queryForList("SELECT id, name, campus, ST_AsText(polygon) AS wkt, status FROM fence ORDER BY campus");
    }

    /** 前端传 [[lng,lat], ...] 闭合多边形，后端转 WKT 覆盖该校区围栏。 */
    @Transactional
    public void saveFence(Integer campus, String name, List<List<Double>> points) {
        if (points == null || points.size() < 3) {
            throw new BizException(ErrorCode.PARAM, "围栏至少需要 3 个点");
        }
        StringBuilder sb = new StringBuilder("POLYGON((");
        for (List<Double> p : points) {
            sb.append(p.get(0)).append(' ').append(p.get(1)).append(',');
        }
        List<Double> first = points.get(0);
        sb.append(first.get(0)).append(' ').append(first.get(1)).append("))");
        jdbc.update("UPDATE fence SET status = 0 WHERE campus = ?", campus);
        jdbc.update("INSERT INTO fence (name, campus, polygon, status, created_at, updated_at) "
                + "VALUES (?,?,ST_GeomFromText(?),1,NOW(),NOW())", name, campus, sb.toString());
        fenceService.reload();
    }

    public BigDecimal todayGmv() {
        return BigDecimal.ZERO;
    }
}
