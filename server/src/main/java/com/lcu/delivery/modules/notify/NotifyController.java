package com.lcu.delivery.modules.notify;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 订阅消息：模板清单给小程序端申请授权用，日志给后台排查用。 */
@RestController
@RequestMapping("/api")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /** 用户端：拿到模板 ID 才能调 uni.requestSubscribeMessage。 */
    @GetMapping("/notify/templates")
    public Result<List<NotifyService.Template>> templates() {
        return Result.ok(notifyService.templates());
    }

    /** 后台：消息发送日志（成功/失败原因一目了然）。 */
    @GetMapping("/admin/notify-logs")
    public Result<Map<String, Object>> logs(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(Map.of("list", notifyService.logs(page, size), "page", page, "size", size));
    }
}
