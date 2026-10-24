package com.lcu.delivery.modules.delivery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.Crypto;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.settle.RiderFinanceService;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.delivery.entity.DeliveryTask;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.entity.DeliveryIncident;
import com.lcu.delivery.modules.delivery.entity.RiderLocation;
import com.lcu.delivery.modules.delivery.mapper.DeliveryTaskMapper;
import com.lcu.delivery.modules.delivery.mapper.DeliveryIncidentMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderLocationMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.user.IdentityService;
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
    private final Crypto crypto;
    private final DeliveryIncidentMapper incidentMapper;
    private final SysConfigService config;
    private final RiderFinanceService riderFinance;

    public DeliveryService(RiderMapper riderMapper, DeliveryTaskMapper taskMapper,
                           RiderLocationMapper locationMapper, OrderMapper orderMapper,
                           OrderService orderService, ShopService shopService, UserService userService,
                           Crypto crypto, DeliveryIncidentMapper incidentMapper,
                           SysConfigService config, RiderFinanceService riderFinance) {
        this.config = config;
        this.riderFinance = riderFinance;
        this.crypto = crypto;
        this.incidentMapper = incidentMapper;
        this.riderMapper = riderMapper;
        this.taskMapper = taskMapper;
        this.locationMapper = locationMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.shopService = shopService;
        this.userService = userService;
    }

    public record ApplyReq(String realName, String idCard, String stuNo, String phone,
                           String idCardFront, String idCardBack, String studentCard,
                           String agreementUrl, Boolean agreeProtocol, String insuranceNo) {
    }

    /**
     * 骑手注册/入驻：姓名、身份证、学号都是敏感字段，必须加密落库（原来直接存明文），
     * 学号另外存 SHA-256 哈希用于查重。被驳回的可以补资料重提。
     */
    @Transactional
    public Rider apply(Long userId, ApplyReq req) {
        Rider exists = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        boolean resubmit = exists != null;
        if (resubmit && exists.getAuditStatus() != null && exists.getAuditStatus() == 1) {
            return exists;
        }

        // 首次提交必须全填；驳回后重新提交允许「留空=沿用原值」，避免反复重填敏感信息
        if (!resubmit) {
            if (isBlank(req.realName())) {
                throw new BizException(ErrorCode.PARAM, "请填写真实姓名");
            }
            if (isBlank(req.idCard()) || !req.idCard().matches("\\d{17}[\\dXx]")) {
                throw new BizException(ErrorCode.PARAM, "身份证号格式不正确（18 位）");
            }
            if (isBlank(req.stuNo()) || !req.stuNo().matches("[A-Za-z0-9]{6,20}")) {
                throw new BizException(ErrorCode.PARAM, "学号/工号需为 6~20 位字母或数字");
            }
            if (isBlank(req.phone()) || !req.phone().matches("\\d{11}")) {
                throw new BizException(ErrorCode.PARAM, "手机号格式不正确");
            }
            if (isBlank(req.idCardFront()) || isBlank(req.idCardBack()) || isBlank(req.studentCard())) {
                throw new BizException(ErrorCode.PARAM, "请上传身份证正反面和校园卡照片");
            }
            if (!Boolean.TRUE.equals(req.agreeProtocol())) {
                throw new BizException(ErrorCode.PARAM, "请先阅读并同意兼职协议");
            }
        } else {
            if (!isBlank(req.idCard()) && !req.idCard().matches("\\d{17}[\\dXx]")) {
                throw new BizException(ErrorCode.PARAM, "身份证号格式不正确（18 位）");
            }
            if (!isBlank(req.stuNo()) && !req.stuNo().matches("[A-Za-z0-9]{6,20}")) {
                throw new BizException(ErrorCode.PARAM, "学号/工号需为 6~20 位字母或数字");
            }
            if (!isBlank(req.phone()) && !req.phone().matches("\\d{11}")) {
                throw new BizException(ErrorCode.PARAM, "手机号格式不正确");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Rider rider = resubmit ? exists : new Rider();
        if (!resubmit) {
            rider.setUserId(userId);
            rider.setWorkStatus(0);
            rider.setDeposit(0);
            rider.setMaxConcurrent(3);
            rider.setScore(new BigDecimal("5.00"));
            rider.setTotalOrders(0);
            rider.setTimeoutOrders(0);
            rider.setCreatedAt(now);
        }
        if (!isBlank(req.realName())) {
            rider.setRealNameEnc(crypto.encrypt(req.realName().trim()));
        }
        if (!isBlank(req.idCard())) {
            rider.setIdCardEnc(crypto.encrypt(req.idCard().trim()));
        }
        if (!isBlank(req.stuNo())) {
            rider.setStuNoEnc(crypto.encrypt(req.stuNo().trim()));
            rider.setStuNoHash(IdentityService.sha256(req.stuNo().trim()));
        }
        if (!isBlank(req.phone())) {
            rider.setPhoneMask(Crypto.maskPhone(req.phone().trim()));
        }
        if (!isBlank(req.idCardFront())) {
            rider.setIdCardFront(req.idCardFront());
        }
        if (!isBlank(req.idCardBack())) {
            rider.setIdCardBack(req.idCardBack());
        }
        if (!isBlank(req.studentCard())) {
            rider.setStudentCard(req.studentCard());
        }
        if (!isBlank(req.agreementUrl())) {
            rider.setAgreementUrl(req.agreementUrl());
        }
        if (!resubmit || Boolean.TRUE.equals(req.agreeProtocol())) {
            rider.setAgreementSignedAt(now);
        }
        if (req.insuranceNo() != null) {
            rider.setInsuranceNo(req.insuranceNo());
        }
        rider.setAuditStatus(0);
        rider.setUpdatedAt(now);
        if (!resubmit) {
            riderMapper.insert(rider);
        } else {
            // updateById 跳过 null，驳回原因必须用 wrapper 清空
            riderMapper.update(null, Wrappers.<Rider>lambdaUpdate()
                    .eq(Rider::getId, rider.getId())
                    .set(Rider::getRealNameEnc, rider.getRealNameEnc())
                    .set(Rider::getIdCardEnc, rider.getIdCardEnc())
                    .set(Rider::getStuNoEnc, rider.getStuNoEnc())
                    .set(Rider::getStuNoHash, rider.getStuNoHash())
                    .set(Rider::getPhoneMask, rider.getPhoneMask())
                    .set(Rider::getIdCardFront, rider.getIdCardFront())
                    .set(Rider::getIdCardBack, rider.getIdCardBack())
                    .set(Rider::getStudentCard, rider.getStudentCard())
                    .set(Rider::getAgreementUrl, rider.getAgreementUrl())
                    .set(Rider::getAgreementSignedAt, rider.getAgreementSignedAt())
                    .set(Rider::getInsuranceNo, rider.getInsuranceNo())
                    .set(Rider::getAuditStatus, 0)
                    .set(Rider::getRejectReason, null)
                    .set(Rider::getUpdatedAt, now));
            rider.setRejectReason(null);
        }
        userService.upgradeRole(userId, "RIDER");
        return rider;
    }

    /** 入驻状态：没提交 / 待审核 / 已通过 / 驳回（带原因），骑手端据此做准入守卫 */
    public Map<String, Object> status(Long userId) {
        Rider r = riderMapper.selectOne(Wrappers.<Rider>lambdaQuery().eq(Rider::getUserId, userId));
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            m.put("applied", false);
            return m;
        }
        m.put("applied", true);
        m.put("riderId", r.getId());
        m.put("auditStatus", r.getAuditStatus());
        m.put("auditText", switch (r.getAuditStatus() == null ? 0 : r.getAuditStatus()) {
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "待审核";
        });
        m.put("rejectReason", r.getRejectReason());
        m.put("phoneMask", r.getPhoneMask());
        m.put("score", r.getScore());
        m.put("totalOrders", r.getTotalOrders());
        m.put("workStatus", r.getWorkStatus());
        m.put("maxConcurrent", r.getMaxConcurrent());
        // 脱敏展示，便于驳回重提时确认「填的是不是同一个人」
        String realName = crypto.decrypt(r.getRealNameEnc());
        String idCard = crypto.decrypt(r.getIdCardEnc());
        String stuNo = crypto.decrypt(r.getStuNoEnc());
        m.put("realNameMask", maskName(realName));
        m.put("idCardMask", maskIdCard(idCard));
        m.put("stuNoMask", maskStuNo(stuNo));
        m.put("idCardFront", r.getIdCardFront());
        m.put("idCardBack", r.getIdCardBack());
        m.put("studentCard", r.getStudentCard());
        m.put("agreementSignedAt", r.getAgreementSignedAt());
        m.put("insuranceNo", r.getInsuranceNo());
        return m;
    }

    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.charAt(0) + (name.length() > 1 ? "**" : "");
    }

    private String maskIdCard(String id) {
        if (id == null || id.length() < 10) {
            return null;
        }
        return id.substring(0, 6) + "********" + id.substring(id.length() - 4);
    }

    private String maskStuNo(String no) {
        if (no == null || no.length() < 6) {
            return null;
        }
        return no.substring(0, 4) + "****" + no.substring(no.length() - 2);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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
            Shop poolShop = shopService.require(o.getShopId());
            m.put("shopName", poolShop.getName());
            m.put("shopLng", poolShop.getLng());
            m.put("shopLat", poolShop.getLat());
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

        // delivery_task 对 order_id 唯一：派单/拒单/超时回收都会留下行，抢单必须复用而不是新插
        DeliveryTask task = taskMapper.selectOne(Wrappers.<DeliveryTask>lambdaQuery()
                .eq(DeliveryTask::getOrderId, orderId).last("limit 1"));
        if (task == null) {
            task = new DeliveryTask();
            task.setOrderId(orderId);
            task.setCreatedAt(LocalDateTime.now());
        } else if (task.getStatus() != null && task.getStatus() >= 1 && task.getStatus() <= 3) {
            throw new BizException(ErrorCode.ORDER_STATUS, "手慢了，这单已经被接走");
        }
        task.setRiderId(rider.getId());
        task.setAssignType(1);
        task.setStatus(1);
        task.setGrabAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
    }

    @Transactional
    public void pickup(Long userId, Long orderId, String photo) {
        if (photo == null || photo.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请先拍取餐照片");
        }
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
        if (photo == null || photo.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请先拍送达照片");
        }
        Rider rider = requireByUser(userId);
        Order delivOrder = orderService.require(orderId);
        orderService.riderDeliver(orderId, rider.getId());
        DeliveryTask task = task(orderId, rider.getId());

        // 累计单量 + 超时判定（readyAt + 配置分钟数），超时自动开罚单并从钱包扣
        boolean timeout = false;
        int timeoutMinutes = config.getInt("delivery.timeout_minutes", 45);
        if (delivOrder.getReadyAt() != null
                && LocalDateTime.now().isAfter(delivOrder.getReadyAt().plusMinutes(timeoutMinutes))) {
            timeout = true;
        }
        jdbcRunner(rider.getId(), timeout);
        if (timeout) {
            int amount = config.getInt("delivery.timeout_penalty", 200);
            riderFinance.addPenalty(rider.getId(), orderId, 1, amount,
                    "超时送达（超过 " + timeoutMinutes + " 分钟）");
        }
        task.setStatus(3);
        task.setDeliveredAt(LocalDateTime.now());
        task.setDeliverPhoto(photo);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        rider.setTotalOrders(rider.getTotalOrders() == null ? 1 : rider.getTotalOrders() + 1);
        riderMapper.updateById(rider);
    }

    public List<Map<String, Object>> myOrders(Long userId, Integer status) {
        Rider rider = requireByUser(userId);
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getRiderId, rider.getId())
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getId)
                .last("limit 50"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            Shop shop = shopService.require(o.getShopId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus());
            m.put("statusText", OrderStatus.text(o.getStatus()));
            m.put("shopId", o.getShopId());
            m.put("shopName", shop.getName());
            m.put("shopLng", shop.getLng());
            m.put("shopLat", shop.getLat());
            m.put("address", o.getAddressJson());
            m.put("lng", o.getLng());
            m.put("lat", o.getLat());
            m.put("riderIncome", o.getRiderIncome());
            m.put("pickedAt", o.getPickedAt());
            m.put("deliveredAt", o.getDeliveredAt());
            m.put("incident", incidentCnt(o.getId()));
            rows.add(m);
        }
        return rows;
    }

    private int incidentCnt(Long orderId) {
        Long c = incidentMapper.selectCount(Wrappers.<DeliveryIncident>lambdaQuery()
                .eq(DeliveryIncident::getOrderId, orderId));
        return c == null ? 0 : c.intValue();
    }

    // ---------------- 配送异常上报 ----------------

    public record IncidentReq(Integer type, String remark, List<String> images) {
    }

    @Transactional
    public Long reportIncident(Long userId, Long orderId, IncidentReq req) {
        Rider rider = requireByUser(userId);
        Order order = orderMapper.selectById(orderId);
        if (order == null || !rider.getId().equals(order.getRiderId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "不是你的订单");
        }
        int type = req.type() == null ? 4 : req.type();
        if (type < 1 || type > 4) {
            throw new BizException(ErrorCode.PARAM, "异常类型不合法");
        }
        DeliveryIncident inc = new DeliveryIncident();
        inc.setOrderId(orderId);
        inc.setRiderId(rider.getId());
        inc.setType(type);
        inc.setRemark(req.remark());
        try {
            inc.setImages(req.images() == null || req.images().isEmpty() ? null
                    : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(req.images()));
        } catch (Exception ignore) {
            inc.setImages(null);
        }
        inc.setStatus(0);
        inc.setCreatedAt(LocalDateTime.now());
        incidentMapper.insert(inc);
        return inc.getId();
    }

    public List<Map<String, Object>> myIncidents(Long userId) {
        Rider rider = requireByUser(userId);
        List<DeliveryIncident> list = incidentMapper.selectList(Wrappers.<DeliveryIncident>lambdaQuery()
                .eq(DeliveryIncident::getRiderId, rider.getId())
                .orderByDesc(DeliveryIncident::getId)
                .last("limit 50"));
        return incidentRows(list);
    }

    public List<Map<String, Object>> allIncidents(Integer status) {
        List<DeliveryIncident> list = incidentMapper.selectList(Wrappers.<DeliveryIncident>lambdaQuery()
                .eq(status != null, DeliveryIncident::getStatus, status)
                .orderByDesc(DeliveryIncident::getId)
                .last("limit 100"));
        return incidentRows(list);
    }

    @Transactional
    public void handleIncident(Long incidentId, String remark) {
        DeliveryIncident inc = incidentMapper.selectById(incidentId);
        if (inc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常记录不存在");
        }
        inc.setStatus(1);
        inc.setHandleRemark(remark);
        incidentMapper.updateById(inc);
    }

    /** 送达时累加累计单量，超时的同时累加超时单数 */
    private void jdbcRunner(Long riderId, boolean timeout) {
        Rider r = riderMapper.selectById(riderId);
        if (r == null) {
            return;
        }
        r.setTotalOrders((r.getTotalOrders() == null ? 0 : r.getTotalOrders()) + 1);
        if (timeout) {
            r.setTimeoutOrders((r.getTimeoutOrders() == null ? 0 : r.getTimeoutOrders()) + 1);
        }
        r.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateById(r);
    }

    private List<String> parseImages(String json) {
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

    private List<Map<String, Object>> incidentRows(List<DeliveryIncident> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DeliveryIncident inc : list) {
            Order o = orderMapper.selectById(inc.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", inc.getId());
            m.put("orderId", inc.getOrderId());
            m.put("orderNo", o == null ? null : o.getOrderNo());
            m.put("shopName", o == null ? null : shopService.require(o.getShopId()).getName());
            m.put("type", inc.getType());
            m.put("typeText", switch (inc.getType() == null ? 4 : inc.getType()) {
                case 1 -> "联系不上用户";
                case 2 -> "餐品撒漏";
                case 3 -> "地址错误";
                default -> "其他";
            });
            m.put("remark", inc.getRemark());
            m.put("images", parseImages(inc.getImages()));
            m.put("status", inc.getStatus());
            m.put("statusText", inc.getStatus() != null && inc.getStatus() == 1 ? "已处理" : "待处理");
            m.put("handleRemark", inc.getHandleRemark());
            m.put("createdAt", inc.getCreatedAt() == null ? null : inc.getCreatedAt().toString().replace('T', ' '));
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
