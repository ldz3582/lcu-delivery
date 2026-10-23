package com.lcu.delivery.modules.user;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 校内身份核验接口。
 * GET /api/identity/config 不鉴权：登录页需要先知道平台有没有开启核验，才能决定要不要显示学号输入框。
 */
@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("required", identityService.required());
        return Result.ok(map);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(identityService.status(UserContext.userId()));
    }

    public record VerifyReq(String stuNo) {
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody VerifyReq req) {
        String mask = identityService.verify(UserContext.userId(), req.stuNo());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("verified", true);
        map.put("stuNoMask", mask);
        return Result.ok(map);
    }
}
