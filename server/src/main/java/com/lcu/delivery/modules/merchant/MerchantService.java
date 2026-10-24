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
import com.lcu.delivery.modules.shop.entity.ProductSku;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import com.lcu.delivery.modules.shop.mapper.ProductSkuMapper;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import com.lcu.delivery.modules.user.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ProductSkuMapper skuMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final UserService userService;

    public MerchantService(ShopMapper shopMapper, ProductMapper productMapper, ProductSkuMapper skuMapper,
                           OrderMapper orderMapper, OrderService orderService, UserService userService) {
        this.skuMapper = skuMapper;
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.userService = userService;
    }

    public record ApplyReq(String name, String logo, Long categoryId, Integer campus, String address,
                           BigDecimal lng, BigDecimal lat, String contactPhone,
                           String licenseUrl, String permitUrl, List<String> photos) {
    }

    @Transactional
    public Shop apply(Long userId, ApplyReq req) {
        Shop exists = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        if (exists != null && exists.getDeletedAt() == null) {
            // 已通过审核的不许改资料重提；被驳回的允许补资料重新提交
            if (exists.getAuditStatus() != null && exists.getAuditStatus() == 1) {
                return exists;
            }
            if (exists.getAuditStatus() != null && exists.getAuditStatus() == 2) {
                applyInto(exists, req);
                // updateById 会跳过 null 字段，驳回原因必须用 update wrapper 显式清空
                shopMapper.update(null, Wrappers.<Shop>lambdaUpdate()
                        .eq(Shop::getId, exists.getId())
                        .set(Shop::getAuditStatus, 0)
                        .set(Shop::getRejectReason, null)
                        .set(Shop::getUpdatedAt, LocalDateTime.now()));
                exists.setAuditStatus(0);
                exists.setRejectReason(null);
                return exists;
            }
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
        shop.setPhotos(toJson(req.photos()));
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

    // ---------------- 规格 SKU ----------------

    public List<ProductSku> skus(Long userId, Long productId) {
        Shop shop = requireShop(userId);
        requireOwnProduct(shop, productId);
        return skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    @Transactional
    public ProductSku createSku(Long userId, Long productId, ProductSku req) {
        Shop shop = requireShop(userId);
        Product p = requireOwnProduct(shop, productId);
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "规格名称不能为空");
        }
        if (req.getPrice() == null || req.getPrice() <= 0) {
            throw new BizException(ErrorCode.PARAM, "规格价格必须大于 0");
        }
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setName(req.getName().trim());
        sku.setPrice(req.getPrice());
        sku.setStock(req.getStock() == null ? -1 : req.getStock());
        sku.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        skuMapper.insert(sku);
        // 有规格的商品以规格价为展示价，取最低规格价
        if (p.getHasSku() == null || p.getHasSku() != 1) {
            p.setHasSku(1);
            p.setUpdatedAt(LocalDateTime.now());
            productMapper.updateById(p);
        }
        refreshMinSkuPrice(p.getId());
        return sku;
    }

    @Transactional
    public ProductSku updateSku(Long userId, Long skuId, ProductSku req) {
        Shop shop = requireShop(userId);
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "规格不存在");
        }
        requireOwnProduct(shop, sku.getProductId());
        if (req.getName() != null && !req.getName().isBlank()) sku.setName(req.getName().trim());
        if (req.getPrice() != null) {
            if (req.getPrice() <= 0) throw new BizException(ErrorCode.PARAM, "规格价格必须大于 0");
            sku.setPrice(req.getPrice());
        }
        if (req.getStock() != null) sku.setStock(req.getStock());
        if (req.getStatus() != null) sku.setStatus(req.getStatus());
        skuMapper.updateById(sku);
        refreshMinSkuPrice(sku.getProductId());
        return sku;
    }

    @Transactional
    public void deleteSku(Long userId, Long skuId) {
        Shop shop = requireShop(userId);
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "规格不存在");
        }
        requireOwnProduct(shop, sku.getProductId());
        skuMapper.deleteById(skuId);
        long left = skuMapper.selectCount(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, sku.getProductId()));
        if (left == 0) {
            Product p = productMapper.selectById(sku.getProductId());
            if (p != null) {
                p.setHasSku(0);
                productMapper.updateById(p);
            }
        } else {
            refreshMinSkuPrice(sku.getProductId());
        }
    }

    private Product requireOwnProduct(Shop shop, Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null || p.getDeletedAt() != null || !shop.getId().equals(p.getShopId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return p;
    }

    /**
     * 展示价改为「读取时取最低规格价」，不再回写 product.price。
     * 这样删掉最后一个规格时展示价会自动回到商品原价，不会卡在旧规格价。
     */
    private void refreshMinSkuPrice(Long productId) {
        // no-op：保留方法名，避免调用点散落
    }

    /** 套餐组成必须是合法 JSON 数组，简单校验一下，防止前端传脏数据 */
    private String normalizeCombo(String combo) {
        if (combo == null || combo.isBlank()) {
            return null;
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(combo);
            return combo;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM, "套餐组成的格式不正确");
        }
    }

    private void applyInto(Shop shop, ApplyReq req) {
        if (req.name() != null && !req.name().isBlank()) {
            shop.setName(req.name());
        }
        if (req.logo() != null) {
            shop.setLogo(req.logo());
        }
        if (req.categoryId() != null) {
            shop.setCategoryId(req.categoryId());
        }
        if (req.campus() != null) {
            shop.setCampus(req.campus());
        }
        if (req.address() != null) {
            shop.setAddress(req.address());
        }
        if (req.lng() != null) {
            shop.setLng(req.lng());
        }
        if (req.lat() != null) {
            shop.setLat(req.lat());
        }
        if (req.contactPhone() != null) {
            shop.setContactPhone(req.contactPhone());
        }
        if (req.licenseUrl() != null) {
            shop.setLicenseUrl(req.licenseUrl());
        }
        if (req.permitUrl() != null) {
            shop.setPermitUrl(req.permitUrl());
        }
        if (req.photos() != null) {
            shop.setPhotos(toJson(req.photos()));
        }
    }

    /**
     * 入驻状态：小程序端进「商家」时先问这个——
     * 没提交过 → 进入驻页；待审核 → 显示审核中；驳回 → 显示原因并可改资料重提。
     */
    public Map<String, Object> status(Long userId) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getOwnerUserId, userId));
        Map<String, Object> m = new LinkedHashMap<>();
        if (shop == null || shop.getDeletedAt() != null) {
            m.put("applied", false);
            return m;
        }
        m.put("applied", true);
        m.put("shopId", shop.getId());
        m.put("name", shop.getName());
        m.put("logo", shop.getLogo());
        m.put("categoryId", shop.getCategoryId());
        m.put("campus", shop.getCampus());
        m.put("address", shop.getAddress());
        m.put("contactPhone", shop.getContactPhone());
        m.put("auditStatus", shop.getAuditStatus());
        m.put("auditText", switch (shop.getAuditStatus() == null ? 0 : shop.getAuditStatus()) {
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "待审核";
        });
        m.put("rejectReason", shop.getRejectReason());
        m.put("bizStatus", shop.getBizStatus());
        m.put("licenseUrl", shop.getLicenseUrl());
        m.put("permitUrl", shop.getPermitUrl());
        m.put("photos", fromJson(shop.getPhotos()));
        return m;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> fromJson(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ObjectMapper().readValue(s, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
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
        p.setHasSku(req.getHasSku() == null ? 0 : req.getHasSku());
        p.setComboItems(normalizeCombo(req.getComboItems()));
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
        if (req.getCategoryId() != null) p.setCategoryId(req.getCategoryId());
        if (req.getOriginPrice() != null) p.setOriginPrice(req.getOriginPrice());
        if (req.getHasSku() != null) p.setHasSku(req.getHasSku());
        boolean clearCombo = false;
        if (req.getComboItems() != null) {
            String combo = normalizeCombo(req.getComboItems());
            if (combo == null) {
                clearCombo = true;
            } else {
                p.setComboItems(combo);
            }
        }
        if (req.getSort() != null) p.setSort(req.getSort());
        p.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(p);
        if (clearCombo) {
            // updateById 会跳过 null 字段，清空套餐必须显式 set null
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, p.getId())
                    .set(Product::getComboItems, null)
                    .set(Product::getUpdatedAt, LocalDateTime.now()));
            p.setComboItems(null);
        }
        return p;
    }
}
