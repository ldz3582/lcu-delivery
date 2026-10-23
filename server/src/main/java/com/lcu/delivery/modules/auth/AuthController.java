package com.lcu.delivery.modules.auth;

import com.lcu.delivery.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/mock-login")
    public Result<AuthService.LoginResp> mockLogin(@RequestBody MockLoginReq req) {
        return Result.ok(authService.mockLogin(req.openid(), req.role()));
    }
}
