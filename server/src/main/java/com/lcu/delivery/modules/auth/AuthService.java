package com.lcu.delivery.modules.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.AuthUser;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.JwtService;
import com.lcu.delivery.config.AppProperties;
import com.lcu.delivery.config.WechatProperties;
import com.lcu.delivery.modules.user.IdentityService;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> ROLES = Set.of("USER", "MERCHANT", "RIDER", "ADMIN");

    private final UserMapper userMapper;
    private final IdentityService identityService;
    private final JwtService jwtService;
    private final WechatClient wechatClient;
    private final AppProperties app;
    private final WechatProperties wechat;

    public AuthService(UserMapper userMapper, IdentityService identityService, JwtService jwtService,
                       WechatClient wechatClient, AppProperties app, WechatProperties wechat) {
        this.userMapper = userMapper;
        this.identityService = identityService;
        this.jwtService = jwtService;
        this.wechatClient = wechatClient;
        this.app = app;
        this.wechat = wechat;
    }

    public record LoginResp(String token, Long userId, String role, String nickname, String avatar) {
    }

    @Transactional
    public LoginResp login(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "缺少登录 code");
        }
        String appid = wechat.miniapp() == null ? null : wechat.miniapp().appid();
        String secret = wechat.miniapp() == null ? null : wechat.miniapp().secret();

        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.PARAM,
                    "后端未配置小程序 AppID/AppSecret，请先在 ops/.env 填写并重启");
        }
        String openid = wechatClient.code2openid(appid, secret, code);
        User user = upsert(openid, null);
        return toResp(user);
    }

    /** 内测用：直接指定 openid 与角色登录，省掉微信授权。生产必须关闭 mock-login。 */
    @Transactional
    public LoginResp mockLogin(String openid, String role) {
        if (!app.mockLogin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前环境未开启模拟登录");
        }
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "openid 不能为空");
        }
        String normalized = role == null ? "USER" : role.toUpperCase();
        if (!ROLES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM, "非法角色");
        }
        User user = upsert(openid, normalized);
        user.setRole(normalized);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toResp(user);
    }

    /**
     * 学号/工号登录（等同于注册）：本地/演示环境用，openid 由学号派生，同一个学号永远进同一个账号。
     * 登录成功即完成校内身份核验。
     */
    @Transactional
    public LoginResp stuLogin(String stuNo) {
        if (!app.mockLogin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前环境未开启学号登录");
        }
        String no = stuNo == null ? "" : stuNo.trim();
        if (!no.matches("[A-Za-z0-9]{6,20}")) {
            throw new BizException(ErrorCode.PARAM, "学号/工号格式不正确（6~20 位字母或数字）");
        }
        String openid = "stu_" + IdentityService.sha256(no).substring(0, 24);
        User user = upsert(openid, "USER");
        identityService.verify(user.getId(), no);
        return toResp(user);
    }

    private User upsert(String openid, String role) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenid, openid));
        if (user != null) {
            return user;
        }
        user = new User();
        user.setOpenid(openid);
        user.setRole(role == null ? "USER" : role);
        user.setNickname("同学" + (int) (Math.random() * 9000 + 1000));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private LoginResp toResp(User user) {
        String token = jwtService.issue(new AuthUser(user.getId(), user.getRole()));
        return new LoginResp(token, user.getId(), user.getRole(), user.getNickname(), user.getAvatar());
    }
}
