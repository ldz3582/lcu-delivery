package com.lcu.delivery.modules.review;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.review.entity.Review;
import com.lcu.delivery.modules.review.mapper.ReviewMapper;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.settle.RiderFinanceService;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 订单评价：一单一评，写入后重算店铺/骑手平均分。 */
@Service
public class ReviewService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ReviewMapper reviewMapper;
    private final OrderService orderService;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbc;
    private final RiderFinanceService riderFinance;
    private final SysConfigService config;
    private final ObjectMapper json = new ObjectMapper();

    public ReviewService(ReviewMapper reviewMapper, OrderService orderService, ShopMapper shopMapper,
                         UserMapper userMapper, OrderMapper orderMapper, JdbcTemplate jdbc,
                         RiderFinanceService riderFinance, SysConfigService config) {
        this.reviewMapper = reviewMapper;
        this.orderService = orderService;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.jdbc = jdbc;
        this.riderFinance = riderFinance;
        this.config = config;
    }

    public record CreateReq(Long orderId, Integer shopScore, Integer riderScore,
                            String content, List<String> images, Boolean anonymous) {
    }

    @Transactional
    public Long create(Long userId, CreateReq req) {
        if (req.orderId() == null) {
            throw new BizException(ErrorCode.PARAM, "缺少订单号");
        }
        if (req.shopScore() == null) {
            throw new BizException(ErrorCode.PARAM, "请先给店铺打分");
        }
        Order order = orderService.requireOwned(userId, req.orderId());
        if (order.getStatus() != OrderStatus.FINISHED) {
            throw new BizException(ErrorCode.ORDER_STATUS, "订单完成之后才能评价");
        }
        Long exists = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery().eq(Review::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "这单已经评价过了");
        }
        Review r = new Review();
        r.setOrderId(order.getId());
        r.setUserId(userId);
        r.setShopId(order.getShopId());
        r.setRiderId(order.getRiderId());
        r.setShopScore(clampScore(req.shopScore()));
        r.setRiderScore(req.riderScore() == null ? null : clampScore(req.riderScore()));
        r.setContent(trimTo(req.content(), 500));
        r.setImages(toJson(req.images()));
        r.setIsAnonymous(Boolean.TRUE.equals(req.anonymous()) ? 1 : 0);
        r.setStatus(1);
        r.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(r);

        refreshShopScore(order.getShopId());
        if (order.getRiderId() != null) {
            refreshRiderScore(order.getRiderId());
            // 差评扣罚：骑手评分 ≤2 星自动开罚单
            if (r.getRiderScore() != null && r.getRiderScore() <= 2) {
                int amount = config.getInt("delivery.bad_review_penalty", 300);
                riderFinance.addPenalty(order.getRiderId(), order.getId(), 2, amount,
                        "差评处罚（" + r.getRiderScore() + " 星）");
            }
        }
        return r.getId();
    }

    /** 店铺详情页的评价列表（公开）。 */
    public PageResult<Map<String, Object>> listByShop(Long shopId, int page, int size) {
        Page<Review> p = reviewMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getShopId, shopId)
                        .eq(Review::getStatus, 1)
                        .orderByDesc(Review::getId));
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    public PageResult<Map<String, Object>> mine(Long userId, int page, int size) {
        Page<Review> p = reviewMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId));
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    public Map<String, Object> byOrder(Long userId, Long orderId) {
        Review r = reviewMapper.selectOne(Wrappers.<Review>lambdaQuery().eq(Review::getOrderId, orderId));
        if (r == null || !userId.equals(r.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有评价");
        }
        return toRows(List.of(r)).get(0);
    }

    /** 商家回复：只能回自己店铺的评价。 */
    public void reply(Long userId, Long reviewId, String reply) {
        if (reply == null || reply.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "回复内容不能为空");
        }
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (shop == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "还没有店铺");
        }
        Review r = reviewMapper.selectById(reviewId);
        if (r == null || !shop.getId().equals(r.getShopId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        r.setReply(trimTo(reply, 500));
        reviewMapper.updateById(r);
    }

    public PageResult<Map<String, Object>> adminList(Integer status, Integer score, int page, int size) {
        var qw = Wrappers.<Review>lambdaQuery()
                .eq(status != null, Review::getStatus, status)
                .ge(score != null, Review::getShopScore, score == null ? 1 : score)
                .orderByDesc(Review::getId);
        Page<Review> p = reviewMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        return new PageResult<>(toRows(p.getRecords()), p.getTotal(), page, size);
    }

    public void updateStatus(Long reviewId, int status) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        r.setStatus(status);
        reviewMapper.updateById(r);
        refreshShopScore(r.getShopId());
        if (r.getRiderId() != null) {
            refreshRiderScore(r.getRiderId());
        }
    }

    // ---------------- 内部 ----------------

    private void refreshShopScore(Long shopId) {
        jdbc.update("UPDATE shop SET score = COALESCE("
                + "  (SELECT ROUND(AVG(shop_score), 2) FROM review WHERE shop_id = ? AND status = 1), score) "
                + "WHERE id = ?", shopId, shopId);
    }

    private void refreshRiderScore(Long riderId) {
        jdbc.update("UPDATE rider SET score = COALESCE("
                + "  (SELECT ROUND(AVG(rider_score), 2) FROM review "
                + "   WHERE rider_id = ? AND status = 1 AND rider_score IS NOT NULL), score) "
                + "WHERE id = ?", riderId, riderId);
    }

    private List<Map<String, Object>> toRows(List<Review> records) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<Long> userIds = records.stream().map(Review::getUserId).distinct().toList();
        List<Long> shopIds = records.stream().map(Review::getShopId).distinct().toList();
        Map<Long, User> users = new LinkedHashMap<>();
        for (User u : userMapper.selectBatchIds(userIds)) {
            users.put(u.getId(), u);
        }
        Map<Long, String> shopNames = new LinkedHashMap<>();
        for (Shop s : shopMapper.selectBatchIds(shopIds)) {
            shopNames.put(s.getId(), s.getName());
        }
        List<Long> orderIds = records.stream().map(Review::getOrderId).distinct().toList();
        Map<Long, String> orderNos = new LinkedHashMap<>();
        for (Order o : orderMapper.selectBatchIds(orderIds)) {
            orderNos.put(o.getId(), o.getOrderNo());
        }
        for (Review r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            boolean anonymous = r.getIsAnonymous() != null && r.getIsAnonymous() == 1;
            User u = users.get(r.getUserId());
            m.put("id", r.getId());
            m.put("orderId", r.getOrderId());
            m.put("orderNo", orderNos.get(r.getOrderId()));
            m.put("shopId", r.getShopId());
            m.put("shopName", shopNames.get(r.getShopId()));
            m.put("shopScore", r.getShopScore());
            m.put("riderScore", r.getRiderScore());
            m.put("content", r.getContent());
            m.put("images", fromJson(r.getImages()));
            m.put("anonymous", anonymous);
            m.put("nickname", anonymous ? "匿名用户" : (u == null ? "同学" : u.getNickname()));
            m.put("avatar", anonymous ? null : (u == null ? null : u.getAvatar()));
            m.put("reply", r.getReply());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().format(TS));
            rows.add(m);
        }
        return rows;
    }

    private int clampScore(Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new BizException(ErrorCode.PARAM, "评分只能是 1~5 星");
        }
        return score;
    }

    private String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
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
