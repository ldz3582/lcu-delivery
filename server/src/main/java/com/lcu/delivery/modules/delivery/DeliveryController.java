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
    private final DispatchService dispatchService;

    public DeliveryController(DeliveryService deliveryService, DispatchService dispatchService) {
        this.deliveryService = deliveryService;
        this.dispatchService = dispatchService;
    }

    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody DeliveryService.ApplyReq req) {
        Rider rider = deliveryService.apply(UserContext.userId(), req);
        return Result.ok(Map.of("riderId", rider.getId(), "auditStatus", rider.getAuditStatus()));
    }

    /** 入驻状态：没提交 / 待审核 / 已通过 / 驳回（带原因） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(deliveryService.status(UserContext.userId()));
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(deliveryService.profile(UserContext.userId()));
    }

    public record SettingsReq(Integer workStatus, Integer maxConcurrent, String workTime) {
    }

    /** 接单设置：是否开启接单 / 同时接单上限 / 可接单时段（需求 4.3.5） */
    @PutMapping("/settings")
    public Result<Map<String, Object>> settings(@RequestBody SettingsReq req) {
        return Result.ok(deliveryService.updateSettings(UserContext.userId(),
                req.workStatus(), req.maxConcurrent(), req.workTime()));
    }

    @GetMapping("/pool")
    public Result<List<Map<String, Object>>> pool(@RequestParam(required = false) BigDecimal lng,
                                                  @RequestParam(required = false) BigDecimal lat) {
        return Result.ok(deliveryService.pool(UserContext.userId(), lng, lat));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> myOrders(@RequestParam(required = false) Integer status) {
        return Result.ok(deliveryService.myOrders(UserContext.userId(), status));
    }

    /** 派给我的单（高峰时段系统派单，需骑手确认） */
    @GetMapping("/dispatched")
    public Result<List<Map<String, Object>>> dispatched() {
        return Result.ok(dispatchService.myDispatched(UserContext.userId()));
    }

    @PostMapping("/dispatched/{id}/accept")
    public Result<Void> acceptDispatch(@PathVariable Long id) {
        dispatchService.accept(UserContext.userId(), id);
        return Result.ok();
    }

    @PostMapping("/dispatched/{id}/reject")
    public Result<Void> rejectDispatch(@PathVariable Long id) {
        dispatchService.reject(UserContext.userId(), id);
        return Result.ok();
    }

    /** 配送异常上报：联系不上用户 / 餐品撒漏 / 地址错误 / 其他 */
    @PostMapping("/orders/{id}/incident")
    public Result<Long> reportIncident(@PathVariable Long id,
                                       @RequestBody DeliveryService.IncidentReq req) {
        return Result.ok(deliveryService.reportIncident(UserContext.userId(), id, req));
    }

    @GetMapping("/incidents")
    public Result<List<Map<String, Object>>> incidents() {
        return Result.ok(deliveryService.myIncidents(UserContext.userId()));
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

    @PostMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        deliveryService.cancel(UserContext.userId(), id);
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
