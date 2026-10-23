package com.lcu.delivery.modules.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
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
public class MerchantService {

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final UserService userService;

    public MerchantService(ShopMapper shopMapper, ProductMapper productMapper, OrderMapper orderMapper,
                           OrderService orderService, UserService userService) {
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.userService = userService;
    }

    public record ApplyReq(String name, String logo, Long categoryId, Integer campus, String address,
                           BigDecimal lng, BigDecimal lat, String contactPhone,
                           String licenseUrl, String permitUrl) {
    }

    @Transactional
    public Shop apply(Long userId, ApplyReq req) {
        Shop exists = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (exists != null) {
            return exists;
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写店铺名称");
        }
        Shop shop = new Shop();
        shop.setOwnerUserId(userId);
        shop.setName(req.name());
        shop.setLogo(req.logo());
        shop.setCategoryId(req.categoryId() == null ? 1L : req.categoryId());
        shop.setCampus(req.campus() == null ? 1 : req.campus());
        shop.setAddress(req.address());
        shop.setLng(req.lng());
        shop.setLat(req.lat());
        shop.setContactPhone(req.contactPhone());
        shop.setLicenseUrl(req.licenseUrl());
        shop.setPermitUrl(req.permitUrl());
        shop.setBizStatus(0);
        shop.setOpenTime("10:30-13:30,16:30-19:30");
        shop.setMinAmount(0);
        shop.setPackFee(0);
        shop.setDeliveryFee(200);
        shop.setCommissionRate(new BigDecimal("0.0600"));
        shop.setScore(new BigDecimal("5.00"));
        shop.setMonthSales(0);
        shop.setAuditStatus(0);
        shop.setSettleStatus(0);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setUpdatedAt(LocalDateTime.now());
        shopMapper.insert(shop);
        userService.upgradeRole(userId, "MERCHANT");
        return shop;
    }

    public Shop requireShop(Long userId) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (shop == null || shop.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有店铺，请先提交入驻");
        }
        return shop;
    }

    @Transactional
    public Shop updateShop(Long userId, Map<String, Object> body) {
        Shop shop = requireShop(userId);
        if (body.containsKey("bizStatus")) {
            shop.setBizStatus(((Number) body.get("bizStatus")).intValue());
        }
        if (body.containsKey("openTime")) {
            shop.setOpenTime(String.valueOf(body.get("openTime")));
        }
        if (body.containsKey("minAmount")) {
            shop.setMinAmount(((Number) body.get("minAmount")).intValue());
        }
        if (body.containsKey("packFee")) {
            shop.setPackFee(((Number) body.get("packFee")).intValue());
        }
        if (body.containsKey("deliveryFee")) {
            shop.setDeliveryFee(((Number) body.get("deliveryFee")).intValue());
        }
        if (body.containsKey("name")) {
            shop.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("logo")) {
            shop.setLogo(String.valueOf(body.get("logo")));
        }
        shop.setUpdatedAt(LocalDateTime.now());
        shopMapper.updateById(shop);
        return shop;
    }

    public PageResult<Map<String, Object>> orders(Long userId, Integer status, int page, int size) {
        Shop shop = requireShop(userId);
        Page<Order> p = orderMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getShopId, shop.getId())
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus());
            m.put("statusText", OrderStatus.text(o.getStatus()));
            m.put("payAmount", o.getPayAmount());
            m.put("shopIncome", o.getShopIncome());
            m.put("address", o.getAddressJson());
            m.put("remark", o.getRemark());
            m.put("createdAt", o.getCreatedAt());
            rows.add(m);
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    public void accept(Long userId, Long orderId) {
        orderService.merchantAccept(orderId, requireShop(userId).getId());
    }

    public void reject(Long userId, Long orderId, String reason) {
        orderService.merchantReject(orderId, requireShop(userId).getId(), reason);
    }

    public void ready(Long userId, Long orderId) {
        orderService.merchantReady(orderId, requireShop(userId).getId());
    }

    public List<Product> products(Long userId, boolean includeOffline) {
        Shop shop = requireShop(userId);
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getShopId, shop.getId())
                .eq(!includeOffline, Product::getStatus, 1)
                .isNull(Product::getDeletedAt)
                .orderByAsc(Product::getSort));
    }

    public Product createProduct(Long userId, Product req) {
        Shop shop = requireShop(userId);
        Product p = new Product();
        p.setShopId(shop.getId());
        p.setCategoryId(req.getCategoryId() == null ? 1L : req.getCategoryId());
        p.setName(req.getName());
        p.setImage(req.getImage());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setOriginPrice(req.getOriginPrice());
        p.setStock(req.getStock() == null ? -1 : req.getStock());
        p.setSales(0);
        p.setHasSku(0);
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setSort(req.getSort() == null ? 0 : req.getSort());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(p);
        return p;
    }

    public Product updateProduct(Long userId, Long productId, Product req) {
        Shop shop = requireShop(userId);
        Product p = productMapper.selectById(productId);
        if (p == null || !shop.getId().equals(p.getShopId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (req.getName() != null) p.setName(req.getName());
        if (req.getImage() != null) p.setImage(req.getImage());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        if (req.getStock() != null) p.setStock(req.getStock());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(p);
        return p;
    }
}
