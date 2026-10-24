package com.lcu.delivery.modules.merchant;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody MerchantService.ApplyReq req) {
        Shop shop = merchantService.apply(UserContext.userId(), req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", shop.getId());
        m.put("auditStatus", shop.getAuditStatus());
        return Result.ok(m);
    }

    /** 入驻状态：没店 / 待审核 / 驳回（带原因） / 已通过 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(merchantService.status(UserContext.userId()));
    }

    @GetMapping("/shop")
    public Result<Map<String, Object>> shop() {
        Shop s = merchantService.requireShop(UserContext.userId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("logo", s.getLogo());
        m.put("bizStatus", s.getBizStatus());
        m.put("auditStatus", s.getAuditStatus());
        m.put("openTime", s.getOpenTime());
        m.put("minAmount", s.getMinAmount());
        m.put("packFee", s.getPackFee());
        m.put("deliveryFee", s.getDeliveryFee());
        m.put("commissionRate", s.getCommissionRate());
        return Result.ok(m);
    }

    @PutMapping("/shop")
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        merchantService.updateShop(UserContext.userId(), body);
        return Result.ok();
    }

    @GetMapping("/orders")
    public Result<PageResult<Map<String, Object>>> orders(@RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(merchantService.orders(UserContext.userId(), status, page, size));
    }

    public record RejectReq(String reason) {
    }

    @PostMapping("/orders/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        merchantService.accept(UserContext.userId(), id);
        return Result.ok();
    }

    @PostMapping("/orders/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) RejectReq req) {
        merchantService.reject(UserContext.userId(), id, req == null ? null : req.reason());
        return Result.ok();
    }

    @PostMapping("/orders/{id}/ready")
    public Result<Void> ready(@PathVariable Long id) {
        merchantService.ready(UserContext.userId(), id);
        return Result.ok();
    }

    // ---------------- 规格 SKU ----------------

    @GetMapping("/products/{id}/skus")
    public Result<List<com.lcu.delivery.modules.shop.entity.ProductSku>> skus(@PathVariable Long id) {
        return Result.ok(merchantService.skus(UserContext.userId(), id));
    }

    @PostMapping("/products/{id}/skus")
    public Result<com.lcu.delivery.modules.shop.entity.ProductSku> createSku(
            @PathVariable Long id,
            @RequestBody com.lcu.delivery.modules.shop.entity.ProductSku sku) {
        return Result.ok(merchantService.createSku(UserContext.userId(), id, sku));
    }

    @PutMapping("/skus/{skuId}")
    public Result<com.lcu.delivery.modules.shop.entity.ProductSku> updateSku(
            @PathVariable Long skuId,
            @RequestBody com.lcu.delivery.modules.shop.entity.ProductSku sku) {
        return Result.ok(merchantService.updateSku(UserContext.userId(), skuId, sku));
    }

    @DeleteMapping("/skus/{skuId}")
    public Result<Void> deleteSku(@PathVariable Long skuId) {
        merchantService.deleteSku(UserContext.userId(), skuId);
        return Result.ok();
    }

    @GetMapping("/products")
    public Result<List<Product>> products(@RequestParam(defaultValue = "true") boolean includeOffline) {
        return Result.ok(merchantService.products(UserContext.userId(), includeOffline));
    }

    @PostMapping("/products")
    public Result<Product> create(@RequestBody Product product) {
        return Result.ok(merchantService.createProduct(UserContext.userId(), product));
    }

    @PutMapping("/products/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return Result.ok(merchantService.updateProduct(UserContext.userId(), id, product));
    }
}
