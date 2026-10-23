package com.lcu.delivery.modules.delivery;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.delivery.entity.Rider;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rider")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody DeliveryService.ApplyReq req) {
        Rider rider = deliveryService.apply(UserContext.userId(), req);
        return Result.ok(Map.of("riderId", rider.getId(), "auditStatus", rider.getAuditStatus()));
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(deliveryService.profile(UserContext.userId()));
    }

    @GetMapping("/pool")
    public Result<List<Map<String, Object>>> pool(@RequestParam(required = false) BigDecimal lng,
                                                  @RequestParam(required = false) BigDecimal lat) {
        return Result.ok(deliveryService.pool(UserContext.userId(), lng, lat));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> myOrders() {
        return Result.ok(deliveryService.myOrders(UserContext.userId()));
    }

    public record PhotoReq(String photo) {
    }

    @PostMapping("/orders/{id}/grab")
    public Result<Void> grab(@PathVariable Long id) {
        deliveryService.grab(UserContext.userId(), id);
        return Result.ok();
    }

    @PostMapping("/orders/{id}/pickup")
    public Result<Void> pickup(@PathVariable Long id, @RequestBody(required = false) PhotoReq req) {
        deliveryService.pickup(UserContext.userId(), id, req == null ? null : req.photo());
        return Result.ok();
    }

    @PostMapping("/orders/{id}/deliver")
    public Result<Void> deliver(@PathVariable Long id, @RequestBody(required = false) PhotoReq req) {
        deliveryService.deliver(UserContext.userId(), id, req == null ? null : req.photo());
        return Result.ok();
    }

    public record LocationReq(BigDecimal lng, BigDecimal lat, Integer accuracy, Long orderId) {
    }

    @PostMapping("/location")
    public Result<Void> location(@RequestBody LocationReq req) {
        deliveryService.reportLocation(UserContext.userId(), req.lng(), req.lat(), req.accuracy(), req.orderId());
        return Result.ok();
    }
}
