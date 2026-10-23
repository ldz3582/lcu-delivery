package com.lcu.delivery.modules.user;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        User user = userService.require(UserContext.userId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("phoneMask", user.getPhoneMask());
        map.put("role", user.getRole());
        return Result.ok(map);
    }

    public record ProfileReq(String nickname, String avatar) {
    }

    @PutMapping("/profile")
    public Result<Void> update(@RequestBody ProfileReq req) {
        userService.updateProfile(UserContext.userId(), req.nickname(), req.avatar());
        return Result.ok();
    }

    public record PhoneReq(String code) {
    }

    /** 真实环境：用 wx.getPhoneNumber 的 code 调微信接口换手机号；本地直接传手机号。 */
    @PostMapping("/phone")
    public Result<Void> bindPhone(@RequestBody PhoneReq req) {
        userService.bindPhone(UserContext.userId(), req.code());
        return Result.ok();
    }
}
