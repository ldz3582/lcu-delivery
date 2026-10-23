package com.lcu.delivery.modules.cart;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端算价：所有价格只信后端，前端传的金额一律忽略。
 */
@Service
public class CartService {

    private final ProductMapper productMapper;
    private final ShopService shopService;

    public CartService(ProductMapper productMapper, ShopService shopService) {
        this.productMapper = productMapper;
        this.shopService = shopService;
    }

    public record Item(Long productId, Long skuId, Integer qty) {
    }

    public record Line(Product product, int qty, int subtotal) {
    }

    public record Bill(Shop shop, List<Line> lines, int goodsAmount, int packFee, int deliveryFee,
                       int discountAmount, int totalAmount, int minAmount, boolean reachMin) {
    }

    public Bill calculate(Long shopId, List<Item> items) {
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
            if (p.getStock() != null && p.getStock() >= 0 && p.getStock() < it.qty()) {
                throw new BizException(ErrorCode.STOCK, p.getName() + " 库存不足");
            }
            int subtotal = p.getPrice() * it.qty();
            goods += subtotal;
            lines.add(new Line(p, it.qty(), subtotal));
        }

        int pack = shop.getPackFee() == null ? 0 : shop.getPackFee();
        int delivery = shop.getDeliveryFee() == null ? 0 : shop.getDeliveryFee();
        int discount = 0;
        int total = goods + pack + delivery - discount;
        int min = shop.getMinAmount() == null ? 0 : shop.getMinAmount();

        return new Bill(shop, lines, goods, pack, delivery, discount, total, min, goods >= min);
    }
}
