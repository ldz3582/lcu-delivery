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

    public ShopService(ShopMapper shopMapper, ProductMapper productMapper,
                       ProductSkuMapper skuMapper, JdbcTemplate jdbc) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, icon, sort FROM shop_category WHERE status = 1 ORDER BY sort");
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
                m.put("distance", Math.round(distance(lng.doubleValue(), lat.doubleValue(),
                        s.getLng().doubleValue(), s.getLat().doubleValue()) * 10) / 10.0);
            }
            rows.add(m);
        }
        if (lng != null) {
            rows.sort(Comparator.comparingDouble(m -> ((Number) m.getOrDefault("distance", 99999)).doubleValue()));
        } else {
            rows.sort(Comparator.comparingInt(m -> -((Number) m.getOrDefault("monthSales", 0)).intValue()));
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    public Shop require(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "店铺不存在");
        }
        return shop;
    }

    public Map<String, Object> detail(Long shopId) {
        Shop shop = require(shopId);
        Map<String, Object> m = toCard(shop);
        m.put("address", shop.getAddress());
        m.put("lng", shop.getLng());
        m.put("lat", shop.getLat());
        m.put("openTime", shop.getOpenTime());
        m.put("contactPhone", shop.getContactPhone());
        m.put("permitUrl", shop.getPermitUrl());
        m.put("products", products(shopId));
        return m;
    }

    public List<Map<String, Object>> products(Long shopId) {
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
            m.put("price", p.getPrice());
            m.put("originPrice", p.getOriginPrice());
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
