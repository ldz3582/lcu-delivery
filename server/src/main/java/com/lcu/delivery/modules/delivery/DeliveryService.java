package com.lcu.delivery.modules.delivery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.delivery.entity.DeliveryTask;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.entity.RiderLocation;
import com.lcu.delivery.modules.delivery.mapper.DeliveryTaskMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderLocationMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryService {

    private final RiderMapper riderMapper;
    private final DeliveryTaskMapper taskMapper;
    private final RiderLocationMapper locationMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ShopService shopService;
    private final UserService userService;

    public DeliveryService(RiderMapper riderMapper, DeliveryTaskMapper taskMapper,
                           RiderLocationMapper locationMapper, OrderMapper orderMapper,
                           OrderService orderService, ShopService shopService, UserService userService) {
        this.riderMapper = riderMapper;
        this.taskMapper = taskMapper;
        this.locationMapper = locationMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.shopService = shopService;
        this.userService = userService;
    }

    public record ApplyReq(String realName, String idCard, String stuNo, String phone) {
    }

    @Transactional
    public Rider apply(Long userId, ApplyReq req) {
        Rider exists = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        if (exists != null) {
            return exists;
        }
        Rider rider = new Rider();
        rider.setUserId(userId);
        rider.setRealNameEnc(req.realName());
        rider.setIdCardEnc(req.idCard());
        rider.setStuNoEnc(req.stuNo());
        rider.setPhoneMask(com.lcu.delivery.common.Crypto.maskPhone(req.phone()));
        rider.setAuditStatus(0);
        rider.setWorkStatus(0);
        rider.setDeposit(0);
        rider.setMaxConcurrent(3);
        rider.setScore(new BigDecimal("5.00"));
        rider.setTotalOrders(0);
        rider.setTimeoutOrders(0);
        rider.setCreatedAt(LocalDateTime.now());
        rider.setUpdatedAt(LocalDateTime.now());
        riderMapper.insert(rider);
        userService.upgradeRole(userId, "RIDER");
        return rider;
    }

    public Rider requireByUser(Long userId) {
        Rider rider = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        if (rider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有提交骑手入驻申请");
        }
        return rider;
    }

    private Rider requireApproved(Long userId) {
        Rider rider = requireByUser(userId);
        if (rider.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "骑手资质审核中，暂不能接单");
        }
        return rider;
    }

    public Map<String, Object> profile(Long userId) {
        Rider r = requireByUser(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("auditStatus", r.getAuditStatus());
        m.put("workStatus", r.getWorkStatus());
        m.put("score", r.getScore());
        m.put("totalOrders", r.getTotalOrders());
        m.put("maxConcurrent", r.getMaxConcurrent());
        m.put("phoneMask", r.getPhoneMask());
        return m;
    }

    /** 抢单大厅：商家已出餐、还没有骑手接的订单。 */
    public List<Map<String, Object>> pool(Long userId, BigDecimal lng, BigDecimal lat) {
        Rider rider = requireApproved(userId);
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.WAIT_PICKUP)
                .isNull(Order::getRiderId)
                .orderByAsc(Order::getReadyAt)
                .last("limit 50"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("shopName", shopService.require(o.getShopId()).getName());
            m.put("deliveryFee", o.getDeliveryFee());
            m.put("riderIncome", o.getRiderIncome());
            m.put("address", o.getAddressJson());
            m.put("readyAt", o.getReadyAt());
            if (lng != null && lat != null && o.getLng() != null) {
                m.put("distance", Math.round(ShopService.distance(lng.doubleValue(), lat.doubleValue(),
                        o.getLng().doubleValue(), o.getLat().doubleValue())));
            }
            rows.add(m);
        }
        if (rider.getWorkStatus() == 0) {
            rider.setWorkStatus(1);
            riderMapper.updateById(rider);
        }
        return rows;
    }

    @Transactional
    public void grab(Long userId, Long orderId) {
        Rider rider = requireApproved(userId);
        long active = taskMapper.selectCount(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getRiderId, rider.getId())
                .in(DeliveryTask::getStatus, 1, 2));
        if (active >= rider.getMaxConcurrent()) {
            throw new BizException(ErrorCode.ORDER_STATUS, "手上订单已达上限（" + rider.getMaxConcurrent() + " 单）");
        }
        orderService.riderGrab(orderId, rider.getId());

        DeliveryTask task = new DeliveryTask();
        task.setOrderId(orderId);
        task.setRiderId(rider.getId());
        task.setAssignType(1);
        task.setStatus(1);
        task.setGrabAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
    }

    @Transactional
    public void pickup(Long userId, Long orderId, String photo) {
        Rider rider = requireByUser(userId);
        orderService.riderPickup(orderId, rider.getId());
        DeliveryTask task = task(orderId, rider.getId());
        task.setStatus(2);
        task.setPickupAt(LocalDateTime.now());
        task.setPickupPhoto(photo);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void deliver(Long userId, Long orderId, String photo) {
        Rider rider = requireByUser(userId);
        orderService.riderDeliver(orderId, rider.getId());
        DeliveryTask task = task(orderId, rider.getId());
        task.setStatus(3);
        task.setDeliveredAt(LocalDateTime.now());
        task.setDeliverPhoto(photo);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        rider.setTotalOrders(rider.getTotalOrders() == null ? 1 : rider.getTotalOrders() + 1);
        riderMapper.updateById(rider);
    }

    public List<Map<String, Object>> myOrders(Long userId) {
        Rider rider = requireByUser(userId);
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getRiderId, rider.getId())
                .orderByDesc(Order::getId)
                .last("limit 50"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus());
            m.put("statusText", OrderStatus.text(o.getStatus()));
            m.put("shopName", shopService.require(o.getShopId()).getName());
            m.put("address", o.getAddressJson());
            m.put("riderIncome", o.getRiderIncome());
            rows.add(m);
        }
        return rows;
    }

    public void reportLocation(Long userId, BigDecimal lng, BigDecimal lat, Integer accuracy, Long orderId) {
        Rider rider = requireByUser(userId);
        RiderLocation loc = new RiderLocation();
        loc.setRiderId(rider.getId());
        loc.setOrderId(orderId);
        loc.setLng(lng);
        loc.setLat(lat);
        loc.setAccuracy(accuracy);
        loc.setReportedAt(LocalDateTime.now());
        locationMapper.insert(loc);
    }

    private DeliveryTask task(Long orderId, Long riderId) {
        DeliveryTask task = taskMapper.selectOne(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getOrderId, orderId).eq(DeliveryTask::getRiderId, riderId));
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配送任务不存在");
        }
        return task;
    }
}
