package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.modules.delivery.DeliveryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 配送异常上报的处置台（联系不上用户 / 餐品撒漏 / 地址错误）。 */
@RestController
@RequestMapping("/api/admin/incidents")
public class AdminIncidentController {

    private final DeliveryService deliveryService;

    public AdminIncidentController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(deliveryService.allIncidents(status));
    }

    public record HandleReq(String remark) {
    }

    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody(required = false) HandleReq req) {
        deliveryService.handleIncident(id, req == null ? null : req.remark());
        return Result.ok();
    }
}
