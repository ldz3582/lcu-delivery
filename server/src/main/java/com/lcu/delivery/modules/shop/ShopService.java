package com.lcu.delivery.modules.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.entity.ProductSku;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import com.lcu.delivery.modules.shop.mapper.ProductSkuMapper;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopService {

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final JdbcTemplate jdbc;
    private final com.lcu.delivery.common.CacheService cache;
    private final com.lcu.delivery.modules.coupon.CouponService couponService;

    public ShopService(ShopMapper shopMapper, ProductMapper productMapper,
                       ProductSkuMapper skuMapper, JdbcTemplate jdbc,
                       com.lcu.delivery.common.CacheService cache,
                       com.lcu.delivery.modules.coupon.CouponService couponService) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.jdbc = jdbc;
        this.cache = cache;
        this.couponService = couponService;
    }

    /** 分类是典型的低频变更高频读取：缓存 5 分钟。 */
    public List<Map<String, Object>> categories() {
        return cache.get("shop:categories", java.time.Duration.ofMinutes(5),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                },
                () -> jdbc.queryForList(
                        "SELECT id, name, icon, sort FROM shop_category WHERE status = 1 ORDER BY sort"));
    }

    public PageResult<Map<String, Object>> list(Integer campus, Long categoryId, String keyword,
                                                BigDecimal lng, BigDecimal lat, int page, int size) {
        String kw = keyword == null ? null : keyword.trim();
        boolean hasKw = kw != null && !kw.isEmpty();

        // 「搜菜品」：先按菜名命中商品，反查店铺；店铺卡片上带出命中的菜名，用户才知道为什么这家店出现
        Map<Long, List<String>> matchedByShop = new LinkedHashMap<>();
        if (hasKw) {
            List<Product> hits = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getStatus, 1)
                    .isNull(Product::getDeletedAt)
                    .like(Product::getName, kw)
                    .last("limit 500"));
            for (Product prod : hits) {
                List<String> names = matchedByShop.computeIfAbsent(prod.getShopId(), k -> new ArrayList<>());
                if (names.size() < 5) {
                    names.add(prod.getName());
                }
            }
        }

        LambdaQueryWrapper<Shop> qw = Wrappers.<Shop>lambdaQuery()
                .eq(Shop::getAuditStatus, 1)
                .isNull(Shop::getDeletedAt)
                .eq(campus != null, Shop::getCampus, campus)
                .eq(categoryId != null, Shop::getCategoryId, categoryId);
        if (hasKw) {
            qw.and(w -> {
                w.like(Shop::getName, kw);
                if (!matchedByShop.isEmpty()) {
                    w.or().in(Shop::getId, matchedByShop.keySet());
                }
            });
        }
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Shop s : p.getRecords()) {
            Map<String, Object> m = toCard(s);
            List<String> matched = matchedByShop.get(s.getId());
            if (matched != null && !matched.isEmpty()) {
                m.put("matchedProducts", matched);
            }
            if (lng != null && lat != null) {
                double meters = distance(lng.doubleValue(), lat.doubleValue(),
                        s.getLng().doubleValue(), s.getLat().doubleValue());
                m.put("distance", Math.round(meters * 10) / 10.0);
                // 预计送达：出餐 10 分钟 + 按 250 米/分钟估算的骑行时间，兜底用平台默认时长
                int ride = (int) Math.ceil(meters / 250.0);
                m.put("etaMinutes", Math.max(5, Math.min(60, 10 + ride)));
            }
            rows.add(m);
        }
        // 排序：营业中的店永远排在休息中的前面；同状态下再按距离（有坐标）或月售
        Comparator<Map<String, Object>> base;
        if (lng != null && lat != null) {
            base = Comparator.comparingDouble(m -> ((Number) m.getOrDefault("distance", 99999)).doubleValue());
        } else {
            base = Comparator.comparingInt(m -> -((Number) m.getOrDefault("monthSales", 0)).intValue());
        }
        rows.sort(Comparator
                .comparingInt((Map<String, Object> m) ->
                        ((Number) m.getOrDefault("bizStatus", 0)).intValue() == 1 ? 0 : 1)
                .thenComparing(base));
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    public Shop require(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "店铺不存在");
        }
        return shop;
    }

    /** 供其它模块（拒单计数、处罚、冻结等）直接更新店铺。 */
    public void updateById(Shop shop) {
        shop.setUpdatedAt(java.time.LocalDateTime.now());
        shopMapper.updateById(shop);
    }

    /**
     * 只取店名，店铺不存在/已软删时返回 null 而不是抛异常。
     * 用于历史订单/异常单的展示：商家注销后后台列表不能被一条脏数据整页打挂。
     */
    public String nameOrNull(Long shopId) {
        if (shopId == null) {
            return null;
        }
        Shop s = shopMapper.selectById(shopId);
        return s == null ? null : s.getName();
    }

    public Map<String, Object> detail(Long shopId, Long userId) {
        Shop shop = require(shopId);
        Map<String, Object> m = toCard(shop);
        m.put("address", shop.getAddress());
        m.put("lng", shop.getLng());
        m.put("lat", shop.getLat());
        m.put("openTime", shop.getOpenTime());
        m.put("contactPhone", shop.getContactPhone());
        m.put("permitUrl", shop.getPermitUrl());
        // 商家公告 / 配送范围（需求 4.1.2、4.2.1）
        m.put("notice", shop.getNotice());
        m.put("deliveryRangeMeters", shop.getDeliveryRangeMeters());
        // 本店满减/折扣活动（需求 4.1.2「店铺满减活动」、4.1.5 领券）
        m.put("coupons", couponService.shopCoupons(shopId, userId));
        m.put("products", products(shopId));
        return m;
    }

    public List<Map<String, Object>> products(Long shopId) {
        return cache.get("shop:products:" + shopId, java.time.Duration.ofSeconds(60),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                },
                () -> loadProducts(shopId));
    }

    private List<Map<String, Object>> loadProducts(Long shopId) {
        List<Product> list = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getShopId, shopId)
                .eq(Product::getStatus, 1)
                .isNull(Product::getDeletedAt)
                .orderByAsc(Product::getSort));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("image", p.getImage());
            m.put("description", p.getDescription());
            m.put("categoryId", p.getCategoryId());
            m.put("price", p.getPrice());
            m.put("originPrice", p.getOriginPrice());
            // 限时特价：specialPrice 为活动价，priceNow 是当前实际成交价
            boolean special = com.lcu.delivery.modules.cart.CartService.specialActive(p);
            m.put("specialPrice", p.getSpecialPrice());
            m.put("specialStartAt", p.getSpecialStartAt());
            m.put("specialEndAt", p.getSpecialEndAt());
            m.put("specialActive", special);
            m.put("priceNow", com.lcu.delivery.modules.cart.CartService.effectivePrice(p));
            m.put("stock", p.getStock());
            m.put("sales", p.getSales());
            m.put("hasSku", p.getHasSku());
            m.put("comboItems", parseCombo(p.getComboItems()));
            if (p.getHasSku() != null && p.getHasSku() == 1) {
                List<ProductSku> skus = skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                        .eq(ProductSku::getProductId, p.getId())
                        .eq(ProductSku::getStatus, 1)
                        .orderByAsc(ProductSku::getId));
                m.put("skus", skus);
                // 展示价取最低规格价（读取时算，不在库里改，删规格自然回落）
                skus.stream().mapToInt(sk -> sk.getPrice() == null ? 0 : sk.getPrice())
                        .filter(v -> v > 0).min().ifPresent(v -> m.put("price", v));
            }
            rows.add(m);
        }
        return rows;
    }

    private List<Map<String, Object>> parseCombo(String combo) {
        if (combo == null || combo.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(combo, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 首页「限时特价」专区：把所有正在特价的菜品按折扣力度排序，带出所属店铺。
     * 只取在售、店铺已通过审核且营业中的。
     */
    public List<Map<String, Object>> specials(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        List<Product> list = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1)
                .isNull(Product::getDeletedAt)
                .isNotNull(Product::getSpecialPrice)
                .gt(Product::getSpecialPrice, 0)
                .last("limit 200"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product p : list) {
            if (!com.lcu.delivery.modules.cart.CartService.specialActive(p)) {
                continue;
            }
            Shop shop = shopMapper.selectById(p.getShopId());
            if (shop == null || shop.getDeletedAt() != null
                    || shop.getAuditStatus() == null || shop.getAuditStatus() != 1
                    || shop.getBizStatus() == null || shop.getBizStatus() != 1) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("name", p.getName());
            m.put("image", p.getImage());
            m.put("price", p.getPrice());
            m.put("specialPrice", p.getSpecialPrice());
            m.put("specialEndAt", p.getSpecialEndAt());
            int origin = p.getPrice() == null ? 0 : p.getPrice();
            int now = p.getSpecialPrice();
            m.put("discountPercent", origin <= 0 ? 100 : (int) Math.round(now * 100.0 / origin));
            m.put("shopId", shop.getId());
            m.put("shopName", shop.getName());
            m.put("campus", shop.getCampus());
            rows.add(m);
        }
        rows.sort((a, b) -> Integer.compare(
                ((Number) a.getOrDefault("discountPercent", 100)).intValue(),
                ((Number) b.getOrDefault("discountPercent", 100)).intValue()));
        return rows.size() > n ? new ArrayList<>(rows.subList(0, n)) : rows;
    }

    private Map<String, Object> toCard(Shop s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("logo", s.getLogo());
        m.put("categoryId", s.getCategoryId());
        m.put("campus", s.getCampus());
        m.put("bizStatus", s.getBizStatus());
        m.put("minAmount", s.getMinAmount());
        m.put("packFee", s.getPackFee());
        m.put("deliveryFee", s.getDeliveryFee());
        m.put("score", s.getScore());
        m.put("monthSales", s.getMonthSales());
        m.put("openTime", s.getOpenTime());
        return m;
    }

    /** Haversine，返回米。 */
    public static double distance(double lng1, double lat1, double lng2, double lat2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
