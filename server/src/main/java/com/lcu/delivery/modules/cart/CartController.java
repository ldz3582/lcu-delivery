package com.lcu.delivery.modules.cart;

import com.lcu.delivery.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    public record CalcReq(Long shopId, List<CartService.Item> items) {
    }

    @PostMapping("/calculate")
    public Result<Map<String, Object>> calculate(@RequestBody CalcReq req) {
        CartService.Bill bill = cartService.calculate(req.shopId(), req.items());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsAmount", bill.goodsAmount());
        data.put("packFee", bill.packFee());
        data.put("deliveryFee", bill.deliveryFee());
        data.put("discountAmount", bill.discountAmount());
        data.put("totalAmount", bill.totalAmount());
        data.put("minAmount", bill.minAmount());
        data.put("reachMin", bill.reachMin());
        data.put("shopName", bill.shop().getName());
        return Result.ok(data);
    }
}
