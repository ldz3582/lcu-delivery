package com.lcu.delivery.modules.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
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
    private final JdbcTemplate jdbc;

    public ShopService(ShopMapper shopMapper, ProductMapper productMapper, JdbcTemplate jdbc) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, icon, sort FROM shop_category WHERE status = 1 ORDER BY sort");
    }

    public PageResult<Map<String, Object>> list(Integer campus, Long categoryId, String keyword,
                                                BigDecimal lng, BigDecimal lat, int page, int size) {
        LambdaQueryWrapper<Shop> qw = Wrappers.<Shop>lambdaQuery()
                .eq(Shop::getAuditStatus, 1)
                .isNull(Shop::getDeletedAt)
                .eq(campus != null, Shop::getCampus, campus)
                .eq(categoryId != null, Shop::getCategoryId, categoryId)
                .like(keyword != null && !keyword.isBlank(), Shop::getName, keyword);
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Shop s : p.getRecords()) {
            Map<String, Object> m = toCard(s);
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
            rows.add(m);
        }
        return rows;
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
