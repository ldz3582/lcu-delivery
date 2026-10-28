package com.lcu.delivery.modules.aftersale;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.aftersale.entity.AfterSale;
import com.lcu.delivery.modules.aftersale.mapper.AfterSaleMapper;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.pay.PayService;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后工单：用户申请 → （商家处理）→ 平台仲裁 → 退款/驳回。
 *
 * 状态：0 待处理、1 处理中、2 已退款、3 已驳回、4 已关闭。
 * 订单侧联动：申请后订单置 70 售后中；退款完成置 80；驳回/关闭回 50 已完成。
 */
@Service
public class AfterSaleService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int REFUNDED = 2;
    public static final int REJECTED = 3;
    public static final int CLOSED = 4;

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final PayService payService;
    private final com.lcu.delivery.modules.admin.SysConfigService config;
    private final ObjectMapper json = new ObjectMapper();

    public AfterSaleService(AfterSaleMapper afterSaleMapper, OrderMapper orderMapper, OrderService orderService,
                            ShopMapper shopMapper, UserMapper userMapper, PayService payService,
                            com.lcu.delivery.modules.admin.SysConfigService config) {
        this.afterSaleMapper = afterSaleMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.payService = payService;
        this.config = config;
    }

    public record CreateReq(Long orderId, Integer type, String reason, Integer amount, List<String> images) {
    }

    @Transactional
    public Long create(Long userId, CreateReq req) {
        if (req.orderId() == null) {
            throw new BizException(ErrorCode.PARAM, "缺少订单号");
        }
        int type = req.type() == null ? 6 : req.type();
        if (type < 1 || type > 6) {
            throw new BizException(ErrorCode.PARAM, "售后类型不合法");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写售后原因");
        }
        Order order = orderService.requireOwned(userId, req.orderId());
        Long active = afterSaleMapper.selectCount(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getOrderId, order.getId())
                .in(AfterSale::getStatus, PENDING, PROCESSING));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "这一单已经有售后在处理中了，请等待处理结果");
        }
        if (order.getStatus() == OrderStatus.AFTER_SALE) {
            throw new BizException(ErrorCode.ORDER_STATUS, "订单已在售后流程中，请勿重复申请");
        }
        if (order.getStatus() != OrderStatus.FINISHED) {
            throw new BizException(ErrorCode.ORDER_STATUS,
                    "订单送达后才能申请售后，当前状态：" + OrderStatus.text(order.getStatus())
                            + "（可先确认收货再申请）");
        }
        // 售后受理期：订单完成后超过 order.complaint_days 天不再受理
        int complaintDays = config.getInt("order.complaint_days", 3);
        LocalDateTime doneAt = order.getFinishedAt() != null ? order.getFinishedAt()
                : (order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getUpdatedAt());
        if (complaintDays > 0 && doneAt != null && doneAt.isBefore(LocalDateTime.now().minusDays(complaintDays))) {
            throw new BizException(ErrorCode.ORDER_STATUS,
                    "订单已完成超过 " + complaintDays + " 天，超出售后受理期，如有疑问请联系客服");
        }
        int payAmount = order.getPayAmount() == null ? 0 : order.getPayAmount();
        int amount = req.amount() == null ? payAmount : req.amount();
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM, "申请退款金额必须大于 0");
        }
        if (amount > payAmount) {
            throw new BizException(ErrorCode.PARAM, "申请金额不能超过订单实付");
        }
        LocalDateTime now = LocalDateTime.now();
        AfterSale as = new AfterSale();
        as.setOrderId(order.getId());
        as.setUserId(userId);
        as.setType(type);
        as.setReason(req.reason().trim());
        as.setImages(toJson(req.images()));
        as.setAmount(amount);
        as.setStatus(PENDING);
        as.setCreatedAt(now);
        as.setUpdatedAt(now);
        afterSaleMapper.insert(as);

        orderService.updateStatus(order, OrderStatus.AFTER_SALE, "用户申请售后：" + typeText(type));
        return as.getId();
    }

    /**
     * 系统自动建工单：商家拒单、接单超时、骑手取消等异常分支都要进后台人工处理池
     * （需求第六章「异常分支全部进入售后工单」）。
     */
    @Transactional
    public Long createSystem(Order order, int type, String reason, List<String> images) {
        Long active = afterSaleMapper.selectCount(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getOrderId, order.getId())
                .in(AfterSale::getStatus, PENDING, PROCESSING));
        if (active != null && active > 0) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        AfterSale as = new AfterSale();
        as.setOrderId(order.getId());
        as.setUserId(order.getUserId());
        as.setType(type);
        as.setReason(reason);
        as.setImages(toJson(images));
        as.setAmount(order.getPayAmount() == null ? 0 : order.getPayAmount());
        as.setStatus(PENDING);
        as.setCreatedAt(now);
        as.setUpdatedAt(now);
        afterSaleMapper.insert(as);
        return as.getId();
    }

    public PageResult<Map<String, Object>> mine(Long userId, Integer status, int page, int size) {        Page<AfterSale> p = afterSaleMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<AfterSale>lambdaQuery()
                        .eq(AfterSale::getUserId, userId)
                        .eq(status != null, AfterSale::getStatus, status)
                        .orderByDesc(AfterSale::getId));
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    public Map<String, Object> detail(Long userId, Long id, boolean admin) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后工单不存在");
        }
        if (!admin && !userId.equals(as.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后工单不存在");
        }
        return toRows(List.of(as)).get(0);
    }

    /** 用户撤销：仅待处理状态可撤，订单回到已完成。 */
    @Transactional
    public void cancel(Long userId, Long id) {
        AfterSale as = requireOwned(userId, id);
        if (as.getStatus() != PENDING) {
            throw new BizException(ErrorCode.ORDER_STATUS, "已受理的工单不能撤销，请等待处理结果");
        }
        close(as, CLOSED, null, "用户撤销售后");
    }

    /** 商家处理：同意则直接退款（小额免仲裁），不同意转平台仲裁。 */
    @Transactional
    public void merchantHandle(Long userId, Long id, boolean agree, String remark) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后工单不存在");
        }
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        Order order = orderService.require(as.getOrderId());
        if (shop == null || !shop.getId().equals(order.getShopId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权处理该工单");
        }
        if (as.getStatus() != PENDING && as.getStatus() != PROCESSING) {
            throw new BizException(ErrorCode.ORDER_STATUS, "工单已结束");
        }
        if (agree) {
            as.setStatus(PROCESSING);
            afterSaleMapper.updateById(as);
            settle(as, order, true, as.getAmount(), 1, remark == null ? "商家同意退款" : remark, userId);
        } else {
            as.setStatus(PROCESSING);
            as.setLiableParty(1);
            as.setJudgeRemark(remark);
            as.setOperatorId(userId);
            as.setUpdatedAt(LocalDateTime.now());
            afterSaleMapper.updateById(as);
        }
    }

    public PageResult<Map<String, Object>> merchantList(Long userId, Integer status, int page, int size) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (shop == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有店铺");
        }
        List<Long> orderIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .eq(Order::getShopId, shop.getId())
                        .select(Order::getId))
                .stream().map(Order::getId).toList();
        if (orderIds.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), 0, page, size);
        }
        Page<AfterSale> p = afterSaleMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<AfterSale>lambdaQuery()
                        .in(AfterSale::getOrderId, orderIds)
                        .eq(status != null, AfterSale::getStatus, status)
                        .orderByDesc(AfterSale::getId));
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    public PageResult<Map<String, Object>> adminList(Integer status, int page, int size) {
        Page<AfterSale> p = afterSaleMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<AfterSale>lambdaQuery()
                        .eq(status != null, AfterSale::getStatus, status)
                        .orderByDesc(AfterSale::getId));
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    /** 平台仲裁：approve=true 走退款（支持部分退），false 驳回并把订单退回已完成。 */
    @Transactional
    public void judge(Long adminId, Long id, boolean approve, Integer amount, Integer liableParty, String remark) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后工单不存在");
        }
        if (as.getStatus() != PENDING && as.getStatus() != PROCESSING) {
            throw new BizException(ErrorCode.ORDER_STATUS, "工单已结束，无需再仲裁");
        }
        Order order = orderService.require(as.getOrderId());
        if (approve) {
            int refund = amount == null || amount <= 0 ? as.getAmount() : amount;
            if (refund > (order.getPayAmount() == null ? 0 : order.getPayAmount())) {
                throw new BizException(ErrorCode.PARAM, "退款金额不能超过订单实付");
            }
            settle(as, order, true, refund, liableParty == null ? 1 : liableParty,
                    remark == null ? "平台仲裁同意退款" : remark, adminId);
        } else {
            settle(as, order, false, 0, liableParty == null ? 1 : liableParty,
                    remark == null ? "平台仲裁驳回" : remark, adminId);
        }
    }

    // ---------------- 内部 ----------------

    /**
     * 统一结案：退款/驳回都要把工单与订单状态对齐。
     * 全额退 → 订单 80；部分退 → 订单回 50，只记为部分赔付。
     *
     * 特别注意「系统自动工单」（商家拒单 / 接单超时 等）：下单时钱就已经退过了、订单已是 80，
     * 这类工单只是留给后台复核责任，**不能再退一次钱，也不能把订单改回已完成**。
     */
    private void settle(AfterSale as, Order order, boolean approve, int amount,
                        int liableParty, String remark, Long operatorId) {
        int payAmount = order.getPayAmount() == null ? 0 : order.getPayAmount();
        boolean alreadyRefunded = order.getStatus() != null && order.getStatus() == OrderStatus.REFUNDED;
        if (alreadyRefunded) {
            // 钱早就退了：工单只做结论，不动订单状态、不动钱包
            as.setStatus(approve ? REFUNDED : REJECTED);
            as.setJudgeRemark((approve ? "确认已退款（系统已自动退款，无需二次退款）：" : "复核不通过（订单已退款，状态不变）：") + remark);
            as.setLiableParty(liableParty);
            as.setOperatorId(operatorId);
            as.setUpdatedAt(LocalDateTime.now());
            afterSaleMapper.updateById(as);
            return;
        }
        if (approve) {
            if (amount >= payAmount) {
                // refundByOperator 内部已把订单置为「已退款」并写流水，这里不要再 updateStatus 一次（会重复写时间轴）
                payService.refundByOperator(order.getId(), amount, remark, 5, operatorId);
            } else {
                payService.refundPartial(order.getId(), amount, remark, 5, operatorId);
                orderService.updateStatus(order, OrderStatus.FINISHED, "部分退款 " + money(amount) + " 元：" + remark);
            }
            as.setStatus(REFUNDED);
        } else {
            orderService.updateStatus(order, OrderStatus.FINISHED, "售后驳回：" + remark);
            as.setStatus(REJECTED);
        }
        as.setLiableParty(liableParty);
        as.setJudgeRemark(remark);
        as.setOperatorId(operatorId);
        as.setUpdatedAt(LocalDateTime.now());
        afterSaleMapper.updateById(as);
    }

    private void close(AfterSale as, int status, Long operatorId, String remark) {
        Order order = orderService.require(as.getOrderId());
        // 系统自动工单（订单已退款）撤销时，不能把订单改回「已完成」
        if (order.getStatus() == null || order.getStatus() != OrderStatus.REFUNDED) {
            orderService.updateStatus(order, OrderStatus.FINISHED, remark);
        }
        as.setStatus(status);
        as.setJudgeRemark(remark);
        as.setOperatorId(operatorId);
        as.setUpdatedAt(LocalDateTime.now());
        afterSaleMapper.updateById(as);
    }

    private AfterSale requireOwned(Long userId, Long id) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null || !userId.equals(as.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后工单不存在");
        }
        return as;
    }

    private List<Map<String, Object>> toRows(List<AfterSale> records) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<Long> orderIds = records.stream().map(AfterSale::getOrderId).distinct().toList();
        Map<Long, Order> orders = new LinkedHashMap<>();
        for (Order o : orderMapper.selectBatchIds(orderIds)) {
            orders.put(o.getId(), o);
        }
        List<Long> shopIds = orders.values().stream().map(Order::getShopId).distinct().toList();
        Map<Long, String> shopNames = new LinkedHashMap<>();
        if (!shopIds.isEmpty()) {
            for (Shop s : shopMapper.selectBatchIds(shopIds)) {
                shopNames.put(s.getId(), s.getName());
            }
        }
        Map<Long, User> users = new LinkedHashMap<>();
        for (Long uid : records.stream().map(AfterSale::getUserId).distinct().toList()) {
            User u = userMapper.selectById(uid);
            if (u != null) {
                users.put(uid, u);
            }
        }
        for (AfterSale as : records) {
            Order o = orders.get(as.getOrderId());
            User u = users.get(as.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", as.getId());
            m.put("orderId", as.getOrderId());
            m.put("orderNo", o == null ? null : o.getOrderNo());
            m.put("shopId", o == null ? null : o.getShopId());
            m.put("shopName", o == null ? null : shopNames.get(o.getShopId()));
            m.put("userId", as.getUserId());
            m.put("nickname", u == null ? null : u.getNickname());
            m.put("type", as.getType());
            m.put("typeText", typeText(as.getType()));
            m.put("reason", as.getReason());
            m.put("images", fromJson(as.getImages()));
            m.put("amount", as.getAmount());
            m.put("payAmount", o == null ? null : o.getPayAmount());
            m.put("status", as.getStatus());
            m.put("statusText", statusText(as.getStatus()));
            m.put("liableParty", as.getLiableParty());
            m.put("judgeRemark", as.getJudgeRemark());
            m.put("createdAt", as.getCreatedAt() == null ? null : as.getCreatedAt().format(TS));
            rows.add(m);
        }
        return rows;
    }

    public static String typeText(Integer type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case 1 -> "少餐";
            case 2 -> "漏餐";
            case 3 -> "餐品变质";
            case 4 -> "撒漏";
            case 5 -> "地址错误";
            default -> "其他";
        };
    }

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING -> "待处理";
            case PROCESSING -> "处理中";
            case REFUNDED -> "已退款";
            case REJECTED -> "已驳回";
            case CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    private String money(int fen) {
        return String.format("%.2f", fen / 100.0);
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(images.subList(0, Math.min(9, images.size())));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> fromJson(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return json.readValue(s, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
