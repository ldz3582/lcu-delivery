package com.lcu.delivery.modules.order;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody OrderService.CreateReq req) {
        return Result.ok(orderService.create(UserContext.userId(), req));
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.list(UserContext.userId(), status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(UserContext.userId(), id));
    }

    /** 订单追踪：时间轴 + 骑手 + 实时位置（用户端 5 秒轮询）。 */
    @GetMapping("/{id}/track")
    public Result<Map<String, Object>> track(@PathVariable Long id) {
        return Result.ok(orderService.track(UserContext.userId(), id));
    }

    public record CancelReq(String reason) {
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) CancelReq req) {
        orderService.cancel(UserContext.userId(), id, req == null ? null : req.reason());
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirmReceive(UserContext.userId(), id);
        return Result.ok();
    }
}
