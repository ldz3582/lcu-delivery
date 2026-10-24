package com.lcu.delivery.modules.delivery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.delivery.entity.DeliveryTask;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.entity.RiderLocation;
import com.lcu.delivery.modules.delivery.mapper.DeliveryTaskMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderLocationMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.notify.NotifyService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能派单（规则版，不是算法模型）：
 *  - 高峰时段（delivery.dispatch_peak_windows）优先派单，闲时仍走自由抢单
 *  - 候选骑手 = 已通过审核 + 在线 + 在手单量未满
 *  - 排序：离店距离近 → 在手单少 → 评分高
 *  - 骑手收到「派给我的单」需确认；超时未确认或拒绝则退回抢单池再派别人
 * 开关：delivery.dispatch_enabled（默认关，随时可在后台打开）
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final OrderMapper orderMapper;
    private final RiderMapper riderMapper;
    private final DeliveryTaskMapper taskMapper;
    private final RiderLocationMapper locationMapper;
    private final ShopService shopService;
    private final SysConfigService config;
    private final NotifyService notifyService;
    private final JdbcTemplate jdbc;

    public DispatchService(OrderMapper orderMapper, RiderMapper riderMapper, DeliveryTaskMapper taskMapper,
                           RiderLocationMapper locationMapper, ShopService shopService, SysConfigService config,
                           NotifyService notifyService, JdbcTemplate jdbc) {
        this.orderMapper = orderMapper;
        this.riderMapper = riderMapper;
        this.taskMapper = taskMapper;
        this.locationMapper = locationMapper;
        this.shopService = shopService;
        this.config = config;
        this.notifyService = notifyService;
        this.jdbc = jdbc;
    }

    public boolean enabled() {
        return config.getBool("delivery.dispatch_enabled", false);
    }

    /** 当前是否处于派单高峰时段，配置形如 "11:00-13:00,17:00-19:00" */
    public boolean inPeakWindow() {
        String windows = config.get("delivery.dispatch_peak_windows", "11:00-13:00,17:00-19:00");
        LocalTime now = LocalTime.now();
        for (String w : windows.split(",")) {
            String[] parts = w.trim().split("-");
            if (parts.length != 2) {
                continue;
            }
            try {
                LocalTime start = LocalTime.parse(parts[0].trim());
                LocalTime end = LocalTime.parse(parts[1].trim());
                if (!now.isBefore(start) && !now.isAfter(end)) {
                    return true;
                }
            } catch (Exception ignore) {
                // 配置写错就跳过这个窗口
            }
        }
        return false;
    }

    public boolean shouldDispatchNow() {
        return enabled() && inPeakWindow();
    }

    /**
     * 给一单派单；成功返回被派骑手 id，没有合适骑手返回 null。
     */
    @Transactional
    public Long dispatch(Order order) {
        if (!shouldDispatchNow()) {
            return null;
        }
        if (order.getRiderId() != null || order.getStatus() == null || order.getStatus() != OrderStatus.WAIT_PICKUP) {
            return null;
        }
        Shop shop = shopService.require(order.getShopId());
        List<Rider> riders = riderMapper.selectList(Wrappers.<Rider>lambdaQuery()
                .eq(Rider::getAuditStatus, 1)
                .in(Rider::getWorkStatus, 1, 2));
        if (riders.isEmpty()) {
            return null;
        }
        // 已经拒过这单的骑手不再派
        List<Long> rejected = new ArrayList<>();
        for (DeliveryTask t : taskMapper.selectList(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getOrderId, order.getId())
                .eq(DeliveryTask::getStatus, 4))) {
            rejected.add(t.getRiderId());
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Rider r : riders) {
            if (rejected.contains(r.getId())) {
                continue;
            }
            long active = taskMapper.selectCount(Wrappers.<DeliveryTask>lambdaQuery()
                    .eq(DeliveryTask::getRiderId, r.getId())
                    .in(DeliveryTask::getStatus, 1, 2));
            int max = r.getMaxConcurrent() == null ? 3 : r.getMaxConcurrent();
            if (active >= max) {
                continue;
            }
            Double distance = null;
            RiderLocation loc = locationMapper.selectOne(Wrappers.<RiderLocation>lambdaQuery()
                    .eq(RiderLocation::getRiderId, r.getId())
                    .orderByDesc(RiderLocation::getId)
                    .last("limit 1"));
            if (loc != null && shop.getLng() != null && shop.getLat() != null) {
                distance = ShopService.distance(loc.getLng().doubleValue(), loc.getLat().doubleValue(),
                        shop.getLng().doubleValue(), shop.getLat().doubleValue());
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("rider", r);
            c.put("active", (int) active);
            c.put("distance", distance);
            candidates.add(c);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator
                .comparingDouble((Map<String, Object> c) -> c.get("distance") == null ? Double.MAX_VALUE
                        : ((Number) c.get("distance")).doubleValue())
                .thenComparingInt(c -> ((Number) c.get("active")).intValue())
                .thenComparing(c -> -((Rider) c.get("rider")).getScore().doubleValue()));

        Rider picked = (Rider) candidates.get(0).get("rider");
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.WAIT_PICKUP)
                .isNull(Order::getRiderId)
                .set(Order::getRiderId, picked.getId())
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            return null;
        }
        // delivery_task 对 order_id 唯一：回收/拒单后再派必须复用同一行
        DeliveryTask task = taskMapper.selectOne(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getOrderId, order.getId()).last("limit 1"));
        if (task == null) {
            task = new DeliveryTask();
            task.setOrderId(order.getId());
            task.setCreatedAt(LocalDateTime.now());
        }
        task.setRiderId(picked.getId());
        task.setAssignType(2);
        task.setStatus(0);
        task.setGrabAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }

        try {
            notifyService.notifyOrderStatus(picked.getUserId(), order.getOrderNo(), order.getId(),
                    "系统派单", "高峰时段优先派单，请及时确认");
        } catch (Exception e) {
            log.debug("派单通知跳过：{}", e.getMessage());
        }
        return picked.getId();
    }

    /** 定时补派：进入待取餐但还没人接的单，逐单尝试派 */
    public int dispatchPending() {
        if (!shouldDispatchNow()) {
            return 0;
        }
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.WAIT_PICKUP)
                .isNull(Order::getRiderId)
                .orderByAsc(Order::getReadyAt)
                .last("limit 20"));
        int n = 0;
        for (Order o : orders) {
            if (dispatch(o) != null) {
                n++;
            }
        }
        return n;
    }

    /** 派单超时未确认：退回抢单池，方便再派别人或自由抢单 */
    public int expireStale() {
        int timeout = config.getInt("delivery.dispatch_timeout_seconds", 120);
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeout);
        int n = 0;
        for (DeliveryTask t : taskMapper.selectList(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getAssignType, 2)
                .eq(DeliveryTask::getStatus, 0)
                .le(DeliveryTask::getCreatedAt, deadline))) {
            t.setStatus(4);
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(t);
            orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                    .eq(Order::getId, t.getOrderId())
                    .eq(Order::getRiderId, t.getRiderId())
                    .isNull(Order::getPickedAt)
                    .set(Order::getRiderId, null)
                    .set(Order::getUpdatedAt, LocalDateTime.now()));
            n++;
        }
        return n;
    }

    // ---------------- 骑手侧 ----------------

    public List<Map<String, Object>> myDispatched(Long userId) {
        Rider rider = requireRider(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DeliveryTask t : taskMapper.selectList(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getRiderId, rider.getId())
                .eq(DeliveryTask::getAssignType, 2)
                .eq(DeliveryTask::getStatus, 0)
                .orderByAsc(DeliveryTask::getId))) {
            Order o = orderMapper.selectById(t.getOrderId());
            if (o == null) {
                continue;
            }
            Shop shop = shopService.require(o.getShopId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("shopName", shop.getName());
            m.put("shopLng", shop.getLng());
            m.put("shopLat", shop.getLat());
            m.put("address", o.getAddressJson());
            m.put("riderIncome", o.getRiderIncome());
            m.put("dispatchedAt", t.getCreatedAt());
            m.put("expireSeconds", config.getInt("delivery.dispatch_timeout_seconds", 120));
            rows.add(m);
        }
        return rows;
    }

    @Transactional
    public void accept(Long userId, Long orderId) {
        Rider rider = requireRider(userId);
        DeliveryTask task = requireTask(orderId, rider.getId());
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getRiderId, rider.getId())
                .isNull(Order::getPickedAt)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "这单状态已变化，请刷新");
        }
        task.setStatus(1);
        task.setGrabAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void reject(Long userId, Long orderId) {
        Rider rider = requireRider(userId);
        DeliveryTask task = requireTask(orderId, rider.getId());
        task.setStatus(4);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getRiderId, rider.getId())
                .isNull(Order::getPickedAt)
                .set(Order::getRiderId, null)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
    }

    private DeliveryTask requireTask(Long orderId, Long riderId) {
        DeliveryTask task = taskMapper.selectOne(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getOrderId, orderId)
                .eq(DeliveryTask::getRiderId, riderId)
                .eq(DeliveryTask::getStatus, 0));
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "没有待确认的派单");
        }
        return task;
    }

    private Rider requireRider(Long userId) {
        Rider r = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        if (r == null || r.getAuditStatus() == null || r.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "骑手资质未通过审核");
        }
        return r;
    }
}
