package com.lcu.delivery.modules.cart;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.coupon.CouponService;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.ProductSku;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ProductSkuMapper;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端算价：所有价格只信后端，前端传的金额一律忽略。
 * 优惠券同样在后端校验并计算抵扣，前端只传 userCouponId。
 */
@Service
public class CartService {

    private final ProductMapper productMapper;
    private final ShopService shopService;
    private final CouponService couponService;
    private final ProductSkuMapper skuMapper;
    private final SysConfigService config;

    public CartService(ProductMapper productMapper, ShopService shopService, CouponService couponService,
                       ProductSkuMapper skuMapper, SysConfigService config) {
        this.productMapper = productMapper;
        this.shopService = shopService;
        this.couponService = couponService;
        this.skuMapper = skuMapper;
        this.config = config;
    }

    public record Item(Long productId, Long skuId, Integer qty) {
    }

    /** price 取的是「实际成交单价」：有规格取规格价，否则取商品价 */
    public record Line(Product product, Long skuId, String skuName, int price, int qty, int subtotal) {
    }

    public record Bill(Shop shop, List<Line> lines, int goodsAmount, int packFee, int deliveryFee,
                       int discountAmount, int totalAmount, int minAmount, boolean reachMin,
                       Long userCouponId, Long couponId, String couponName) {
    }

    public Bill calculate(Long shopId, List<Item> items) {
        return calculate(null, shopId, items, null);
    }

    /**
     * @param userId       用券时的归属校验；不用券可以传 null
     * @param userCouponId 用户券 id（user_coupon.id），不是券模板 id
     */
    public Bill calculate(Long userId, Long shopId, List<Item> items, Long userCouponId) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM, "购物车是空的");
        }
        Shop shop = shopService.require(shopId);
        if (shop.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "店铺暂不可用");
        }
        if (shop.getBizStatus() != 1) {
            throw new BizException(ErrorCode.SHOP_CLOSED, "商家休息中");
        }

        List<Line> lines = new ArrayList<>();
        int goods = 0;
        for (Item it : items) {
            if (it.qty() == null || it.qty() <= 0) {
                throw new BizException(ErrorCode.PARAM, "数量不合法");
            }
            Product p = productMapper.selectById(it.productId());
            if (p == null || p.getDeletedAt() != null || p.getStatus() != 1 || !shopId.equals(p.getShopId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "商品已下架");
            }
            int price;
            String skuName = null;
            Long skuId = null;
            if (p.getHasSku() != null && p.getHasSku() == 1) {
                if (it.skuId() == null) {
                    throw new BizException(ErrorCode.PARAM, "请选择 " + p.getName() + " 的规格");
                }
                ProductSku sku = skuMapper.selectById(it.skuId());
                if (sku == null || !p.getId().equals(sku.getProductId())
                        || sku.getStatus() == null || sku.getStatus() != 1) {
                    throw new BizException(ErrorCode.NOT_FOUND, "规格已下架，请重新选择");
                }
                if (sku.getStock() != null && sku.getStock() >= 0 && sku.getStock() < it.qty()) {
                    throw new BizException(ErrorCode.STOCK, p.getName() + "（" + sku.getName() + "）库存不足");
                }
                price = sku.getPrice();
                skuName = sku.getName();
                skuId = sku.getId();
            } else {
                if (p.getStock() != null && p.getStock() >= 0 && p.getStock() < it.qty()) {
                    throw new BizException(ErrorCode.STOCK, p.getName() + " 库存不足");
                }
                price = effectivePrice(p);
            }
            int subtotal = price * it.qty();
            goods += subtotal;
            lines.add(new Line(p, skuId, skuName, price, it.qty(), subtotal));
        }

        int pack = shop.getPackFee() == null ? 0 : shop.getPackFee();
        int delivery = deliveryFeeOf(shop);

        CouponService.Applied applied = null;
        if (userCouponId != null) {
            if (userId == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录再使用优惠券");
            }
            applied = couponService.apply(userId, userCouponId, shopId, goods);
        }
        int discount = applied == null ? 0 : applied.discount();
        int total = Math.max(0, goods + pack + delivery - discount);
        int min = shop.getMinAmount() == null ? 0 : shop.getMinAmount();

        return new Bill(shop, lines, goods, pack, delivery, discount, total, min, goods >= min,
                applied == null ? null : applied.userCouponId(),
                applied == null ? null : applied.couponId(),
                applied == null ? null : applied.name());
    }

    /** 菜品实际成交单价：命中限时特价时段就按特价，否则原价。 */
    public static int effectivePrice(Product p) {
        Integer sp = p.getSpecialPrice();
        if (sp == null || sp <= 0) {
            return p.getPrice() == null ? 0 : p.getPrice();
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (p.getSpecialStartAt() != null && now.isBefore(p.getSpecialStartAt())) {
            return p.getPrice() == null ? 0 : p.getPrice();
        }
        if (p.getSpecialEndAt() != null && now.isAfter(p.getSpecialEndAt())) {
            return p.getPrice() == null ? 0 : p.getPrice();
        }
        return sp;
    }

    /** 特价是否正在生效（前端打「限时特价」标签用）。 */
    public static boolean specialActive(Product p) {
        return p.getSpecialPrice() != null && p.getSpecialPrice() > 0 && effectivePrice(p) == p.getSpecialPrice();
    }

    /**
     * 配送费取值规则（后台可配）：
     *  - delivery.fee_mode = platform：全平台统一用 delivery.base_fee
     *  - delivery.fee_mode = shop（默认）：按店铺 delivery_fee，店铺没设则用平台 base_fee
     */
    public int deliveryFeeOf(Shop shop) {
        int base = config.getInt("delivery.base_fee", 200);
        String mode = config.get("delivery.fee_mode", "shop");
        if ("platform".equalsIgnoreCase(mode)) {
            return base;
        }
        Integer shopFee = shop.getDeliveryFee();
        return shopFee == null || shopFee <= 0 ? base : shopFee;
    }
}
