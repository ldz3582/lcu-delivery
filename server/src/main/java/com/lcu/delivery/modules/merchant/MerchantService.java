package com.lcu.delivery.modules.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.CacheService;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.delivery.DispatchService;
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
    private final DispatchService dispatchService;
    private final SysConfigService config;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final CacheService cache;

    public MerchantService(ShopMapper shopMapper, ProductMapper productMapper, ProductSkuMapper skuMapper,
                           OrderMapper orderMapper, OrderService orderService, UserService userService,
                           DispatchService dispatchService, SysConfigService config,
                           org.springframework.jdbc.core.JdbcTemplate jdbc, CacheService cache) {
        this.dispatchService = dispatchService;
        this.skuMapper = skuMapper;
        this.shopMapper = shopMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.userService = userService;
        this.config = config;
        this.jdbc = jdbc;
        this.cache = cache;
    }

    /** 抽佣默认值：校内（食堂/饮品/超市）按 commission.rate_campus，校外小吃街(category=2)按 commission.rate_off_campus。 */
    /** 字段长度统一校验：避免超长内容打到数据库里变成 500。 */
    private void checkLen(String v, int max, String label) {
        if (v != null && v.trim().length() > max) {
            throw new BizException(ErrorCode.PARAM, label + "最多 " + max + " 个字符");
        }
    }

    private BigDecimal defaultCommission(Long categoryId) {
        String key = (categoryId != null && categoryId == 2L) ? "commission.rate_off_campus" : "commission.rate_campus";
        String def = (categoryId != null && categoryId == 2L) ? "0.1000" : "0.0600";
        try {
            return new BigDecimal(config.get(key, def)).setScale(4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal(def);
        }
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
        // 资质与坐标是硬门槛（需求 4.2.1 / 9.1「无证一律禁止入驻」）
        if (req.contactPhone() == null || req.contactPhone().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写负责人手机号");
        }
        if (req.lng() == null || req.lat() == null) {
            throw new BizException(ErrorCode.PARAM, "请在地图上标记门店位置");
        }
        if (req.licenseUrl() == null || req.licenseUrl().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请上传营业执照");
        }
        if (req.permitUrl() == null || req.permitUrl().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请上传食品经营许可证");
        }
        if (req.photos() == null || req.photos().isEmpty()) {
            throw new BizException(ErrorCode.PARAM, "请至少上传 1 张门店照片");
        }
        checkLen(req.name(), 64, "店铺名称");
        if (req.address() != null) {
            checkLen(req.address(), 255, "店铺地址");
        }
        Shop shop = new Shop();
        shop.setOwnerUserId(userId);
        shop.setName(req.name().trim());
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
        shop.setDeliveryFee(config.getInt("delivery.base_fee", 200));
        // 抽佣按商户类型差异化：校外小吃街(category=2) 高于校内食堂/超市
        shop.setCommissionRate(defaultCommission(shop.getCategoryId()));
        shop.setRejectCount(0);
        shop.setPushEnabled(1);
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
        evictShopCache();
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
        evictShopCache();
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
        evictShopCache();
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
            String n = String.valueOf(body.get("name"));
            checkLen(n, 64, "店铺名称");
            shop.setName(n);
        }
        if (body.containsKey("logo")) {
            shop.setLogo(String.valueOf(body.get("logo")));
        }
        // 店铺公告 / 接收消息推送 / 配送范围（需求 4.2.6、4.2.1）
        if (body.containsKey("notice")) {
            String notice = body.get("notice") == null ? null : String.valueOf(body.get("notice"));
            checkLen(notice, 500, "店铺公告");
            shop.setNotice(notice);
        }
        if (body.containsKey("pushEnabled")) {
            shop.setPushEnabled(((Number) body.get("pushEnabled")).intValue());
        }
        if (body.containsKey("deliveryRangeMeters")) {
            Object v = body.get("deliveryRangeMeters");
            shop.setDeliveryRangeMeters(v == null ? null : ((Number) v).intValue());
        }
        shop.setUpdatedAt(LocalDateTime.now());
        shopMapper.updateById(shop);
        // updateById 跳过 null：要「清空配送范围」必须显式写 NULL，否则前端传 null 读回来还是旧值
        if (body.containsKey("deliveryRangeMeters") && body.get("deliveryRangeMeters") == null) {
            shopMapper.update(null, Wrappers.<Shop>lambdaUpdate()
                    .eq(Shop::getId, shop.getId())
                    .set(Shop::getDeliveryRangeMeters, null)
                    .set(Shop::getUpdatedAt, LocalDateTime.now()));
            shop.setDeliveryRangeMeters(null);
        }
        return shop;
    }

    /** 商家订单详情：商品清单 + 金额明细（需求 4.2.3「订单详情」）。 */
    public Map<String, Object> orderDetail(Long userId, Long orderId) {
        Shop shop = requireShop(userId);
        Order o = orderMapper.selectById(orderId);
        if (o == null || !shop.getId().equals(o.getShopId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("status", o.getStatus());
        m.put("statusText", OrderStatus.text(o.getStatus()));
        m.put("address", o.getAddressJson());
        m.put("remark", o.getRemark());
        m.put("goodsAmount", o.getGoodsAmount());
        m.put("packFee", o.getPackFee());
        m.put("deliveryFee", o.getDeliveryFee());
        m.put("discountAmount", o.getDiscountAmount());
        m.put("payAmount", o.getPayAmount());
        m.put("commission", o.getCommission());
        m.put("shopIncome", o.getShopIncome());
        m.put("createdAt", o.getCreatedAt());
        m.put("paidAt", o.getPaidAt());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT name, price, qty, subtotal FROM order_item WHERE order_id = ? ORDER BY id", orderId)) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", r.get("name"));
            it.put("price", r.get("price"));
            it.put("qty", r.get("qty"));
            it.put("subtotal", r.get("subtotal"));
            items.add(it);
        }
        m.put("items", items);
        return m;
    }

    // ---------- 菜品分类管理（需求 4.2.2） ----------
    public List<Map<String, Object>> categories(Long userId) {
        Shop shop = requireShop(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT c.id, c.name, c.sort, (SELECT COUNT(*) FROM product p WHERE p.category_id = c.id "
                        + "AND p.deleted_at IS NULL) products FROM product_category c WHERE c.shop_id = ? "
                        + "ORDER BY c.sort ASC, c.id ASC", shop.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("sort", r.get("sort"));
            m.put("productCount", r.get("products"));
            rows.add(m);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveCategory(Long userId, Long id, String name, Integer sort) {
        Shop shop = requireShop(userId);
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "分类名称不能为空");
        }
        if (id == null) {
            jdbc.update("INSERT INTO product_category (shop_id, name, sort) VALUES (?,?,?)",
                    shop.getId(), name.trim(), sort == null ? 0 : sort);
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            Integer own = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_category WHERE id = ? AND shop_id = ?", Integer.class, id, shop.getId());
            if (own == null || own == 0) {
                throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
            }
            jdbc.update("UPDATE product_category SET name = ?, sort = ? WHERE id = ?",
                    name.trim(), sort == null ? 0 : sort, id);
        }
        return Map.of("id", id, "name", name.trim(), "sort", sort == null ? 0 : sort);
    }

    @Transactional
    public void deleteCategory(Long userId, Long id) {
        Shop shop = requireShop(userId);
        Integer own = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_category WHERE id = ? AND shop_id = ?", Integer.class, id, shop.getId());
        if (own == null || own == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        Integer used = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE category_id = ? AND deleted_at IS NULL", Integer.class, id);
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PARAM, "该分类下还有 " + used + " 个菜品，请先移走再删除");
        }
        jdbc.update("DELETE FROM product_category WHERE id = ?", id);
    }

    /** 菜品/店铺改动后清掉 Redis 里的商品快照，避免用户端 60 秒内看到旧价格/旧库存。 */
    private void evictShopCache() {
        cache.evictPrefix("shop:products:");
    }

    /** 库存预警：库存为 0 或低于阈值的在售菜品（需求 4.2.2）。 */    public List<Map<String, Object>> stockAlerts(Long userId, Integer threshold) {
        Shop shop = requireShop(userId);
        int t = threshold == null ? 5 : Math.max(0, threshold);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, name, stock, status FROM product WHERE shop_id = ? AND deleted_at IS NULL "
                        + "AND stock >= 0 AND stock <= ? ORDER BY stock ASC, id ASC", shop.getId(), t)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", r.get("id"));
            m.put("name", r.get("name"));
            m.put("stock", r.get("stock"));
            m.put("status", r.get("status"));
            m.put("statusText", ((Number) r.get("status")).intValue() == 1 ? "在售" : "已下架");
            m.put("soldOut", ((Number) r.get("stock")).intValue() == 0);
            rows.add(m);
        }
        return rows;
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
        // 高峰时段优先智能派单；没派出去就留在抢单大厅（闲时自由抢单）
        try {
            dispatchService.dispatch(orderService.require(orderId));
        } catch (Exception ignore) {
            // 派单失败不影响出餐
        }
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
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写菜品名称");
        }
        checkLen(req.getName(), 64, "菜品名称");
        checkLen(req.getDescription(), 500, "菜品描述");
        if (req.getImage() != null) {
            checkLen(req.getImage(), 512, "图片地址");
        }
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
        if (req.getSpecialPrice() != null && req.getSpecialPrice() > 0) {
            int origin = req.getPrice() == null ? 0 : req.getPrice();
            if (req.getSpecialPrice() >= origin) {
                throw new BizException(ErrorCode.PARAM, "特价必须低于原价");
            }
            p.setSpecialPrice(req.getSpecialPrice());
            p.setSpecialStartAt(req.getSpecialStartAt());
            p.setSpecialEndAt(req.getSpecialEndAt());
        }
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        p.setSort(req.getSort() == null ? 0 : req.getSort());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(p);
        evictShopCache();
        return p;
    }

    public Product updateProduct(Long userId, Long productId, Product req) {
        Shop shop = requireShop(userId);
        Product p = productMapper.selectById(productId);
        if (p == null || !shop.getId().equals(p.getShopId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (req.getName() != null) {
            checkLen(req.getName(), 64, "菜品名称");
            p.setName(req.getName());
        }
        if (req.getDescription() != null) {
            checkLen(req.getDescription(), 500, "菜品描述");
        }
        if (req.getImage() != null) p.setImage(req.getImage());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        if (req.getStock() != null) p.setStock(req.getStock());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        if (req.getCategoryId() != null) p.setCategoryId(req.getCategoryId());
        if (req.getOriginPrice() != null) p.setOriginPrice(req.getOriginPrice());
        if (req.getHasSku() != null) p.setHasSku(req.getHasSku());
        // 限时特价：specialPrice<=0 视为关闭特价
        boolean clearSpecial = false;
        if (req.getSpecialPrice() != null) {
            if (req.getSpecialPrice() <= 0) {
                p.setSpecialPrice(null);
                p.setSpecialStartAt(null);
                p.setSpecialEndAt(null);
                clearSpecial = true;
            } else {
                int origin = req.getPrice() != null ? req.getPrice() : (p.getPrice() == null ? 0 : p.getPrice());
                if (req.getSpecialPrice() >= origin) {
                    throw new BizException(ErrorCode.PARAM, "特价必须低于原价");
                }
                if (req.getSpecialStartAt() != null && req.getSpecialEndAt() != null
                        && !req.getSpecialEndAt().isAfter(req.getSpecialStartAt())) {
                    throw new BizException(ErrorCode.PARAM, "特价结束时间必须晚于开始时间");
                }
                p.setSpecialPrice(req.getSpecialPrice());
                p.setSpecialStartAt(req.getSpecialStartAt());
                p.setSpecialEndAt(req.getSpecialEndAt());
            }
        }
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
        if (clearSpecial) {
            // 同理：updateById 跳过 null，关闭特价必须显式把三列写成 NULL
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, p.getId())
                    .set(Product::getSpecialPrice, null)
                    .set(Product::getSpecialStartAt, null)
                    .set(Product::getSpecialEndAt, null)
                    .set(Product::getUpdatedAt, LocalDateTime.now()));
        }
        evictShopCache();
        return p;
    }
}
