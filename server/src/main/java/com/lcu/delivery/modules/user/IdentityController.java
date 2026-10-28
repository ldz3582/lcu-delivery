package com.lcu.delivery.modules.user;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.admin.SysConfigService;
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
    private final SysConfigService config;

    public IdentityController(IdentityService identityService, SysConfigService config) {
        this.identityService = identityService;
        this.config = config;
    }

    /** 顺便把前端要用到的平台开关一起下发，省一次请求。 */
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("required", identityService.required());
        map.put("addressParse", config.getBool("feature.address_parse", true));
        map.put("fenceEnforce", config.getBool("delivery.fence_enforce", true));
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
