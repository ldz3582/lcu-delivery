package com.lcu.delivery.modules.auth;

import com.lcu.delivery.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginReq(@NotBlank(message = "缺少登录 code") String code) {
    }

    public record MockLoginReq(String openid, String role) {
    }

    @PostMapping("/login")
    public Result<AuthService.LoginResp> login(@RequestBody @Valid LoginReq req) {
        return Result.ok(authService.login(req.code()));
    }

    public record StuLoginReq(String stuNo) {
    }

    /** 学号/工号登录（注册）：演示与本地环境使用 */
    @PostMapping("/stu-login")
    public Result<AuthService.LoginResp> stuLogin(@RequestBody StuLoginReq req) {
        return Result.ok(authService.stuLogin(req.stuNo()));
    }

    /** 演示账号（仅 mock 环境）：商家端可选具体哪家店、骑手端可选哪个骑手。 */
    @GetMapping("/mock-accounts")
    public Result<List<Map<String, Object>>> mockAccounts(@RequestParam(defaultValue = "MERCHANT") String role) {
        return Result.ok(authService.mockAccounts(role));
    }

    @PostMapping("/mock-login")
    public Result<AuthService.LoginResp> mockLogin(@RequestBody MockLoginReq req) {
        return Result.ok(authService.mockLogin(req.openid(), req.role()));
    }

    /**
     * 平台侧（H5 / 浏览器）登录第一步：拿公众号网页授权地址。
     * 返回 data=null 表示后端没配公众号，前端应退回学号登录。
     */
    @GetMapping("/oauth-url")
    public Result<String> oauthUrl(@RequestParam String redirect) {
        return Result.ok(authService.oauthUrl(redirect));
    }

    public record OAuthLoginReq(String code) {
    }

    /** 平台侧登录第二步：授权回调带回的 code 换登录态（unionid 关联小程序账号）。 */
    @PostMapping("/oauth-login")
    public Result<AuthService.LoginResp> oauthLogin(@RequestBody OAuthLoginReq req) {
        return Result.ok(authService.oauthLogin(req.code()));
    }
}
