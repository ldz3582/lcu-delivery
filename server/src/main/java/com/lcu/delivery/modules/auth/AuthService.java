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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> ROLES = Set.of("USER", "MERCHANT", "RIDER", "ADMIN");

    private final UserMapper userMapper;
    private final IdentityService identityService;
    private final JwtService jwtService;
    private final WechatClient wechatClient;
    private final JdbcTemplate jdbc;
    private final AppProperties app;
    private final WechatProperties wechat;

    public AuthService(UserMapper userMapper, IdentityService identityService, JwtService jwtService,
                       WechatClient wechatClient, AppProperties app, WechatProperties wechat,
                        JdbcTemplate jdbc) {
        this.userMapper = userMapper;
        this.identityService = identityService;
        this.jwtService = jwtService;
        this.wechatClient = wechatClient;
        this.app = app;
        this.wechat = wechat;
        this.jdbc = jdbc;
    }

    public record LoginResp(String token, Long userId, String role, String nickname, String avatar) {
    }

    /**
     * 演示账号列表：本地没有 AppID 时，商家/骑手端要能选「登哪家店 / 哪个骑手」。
     * 只在 app.mock-login=true 时有数据，真实环境返回空数组，前端自动退回单账号入口。
     */
    public List<Map<String, Object>> mockAccounts(String role) {
        if (!app.mockLogin()) {
            return List.of();
        }
        String r = role == null ? "MERCHANT" : role.trim().toUpperCase();
        if ("MERCHANT".equals(r)) {
            return jdbc.queryForList(
                    "SELECT u.openid AS openid, u.nickname AS nickname, s.id AS shopId, s.name AS shopName, "
                            + "s.campus AS campus, s.audit_status AS auditStatus, s.biz_status AS bizStatus "
                            + "FROM shop s JOIN `user` u ON u.id = s.owner_user_id "
                            + "WHERE s.deleted_at IS NULL ORDER BY s.id");
        }
        if ("RIDER".equals(r)) {
            return jdbc.queryForList(
                    "SELECT u.openid AS openid, u.nickname AS nickname, r.id AS riderId, u.nickname AS riderName, "
                            + "r.audit_status AS auditStatus, r.score AS score "
                            + "FROM rider r JOIN `user` u ON u.id = r.user_id ORDER BY r.id");
        }
        return List.of();
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
        requireActive(user);
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
        requireActive(user);
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
        requireActive(user);
        identityService.verify(user.getId(), no);
        return toResp(user);
    }

    /**
     * 公众号网页授权地址。配置了 wechat.official 才返回真实地址；
     * 否则返回 null，前端退回「学号登录 / 演示账号」。
     */
    public String oauthUrl(String redirectUri) {
        WechatProperties.Official official = wechat.official();
        if (official == null || official.appid() == null || official.appid().isBlank()) {
            return null;
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "缺少 redirect 参数");
        }
        return wechatClient.officialAuthUrl(official.appid(), redirectUri, "lcu");
    }

    /**
     * 平台侧（浏览器 / 微信内置浏览器）登录。
     *
     * <p>真实模式：公众号网页授权的 code → openid + unionid，用 unionid 关联小程序账号，
     * 保证同一个人在「小程序」和「网页」里是同一个用户、同一个钱包。
     *
     * <p>mock 模式：把 code 当成 openid 用，方便本地把 H5 登录流程跑通。
     */
    @Transactional
    public LoginResp oauthLogin(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "缺少授权 code");
        }
        WechatProperties.Official official = wechat.official();
        boolean configured = official != null && official.appid() != null && !official.appid().isBlank()
                && official.secret() != null && !official.secret().isBlank();

        String mpOpenid;
        String unionid = null;
        if (configured) {
            WechatClient.OAuthUser u = wechatClient.officialOpenid(official.appid(), official.secret(), code);
            mpOpenid = u.openid();
            unionid = u.unionid();
        } else if (app.mockLogin()) {
            // mock：把 code 直接当公众号 openid 用（findOrCreateByOAuth 会补 mp_ 前缀）
            mpOpenid = code.trim();
        } else {
            throw new BizException(ErrorCode.PARAM,
                    "后端未配置公众号 AppID/AppSecret（WX_MP_APPID / WX_MP_SECRET），无法在网页端登录");
        }

        User user = findOrCreateByOAuth(mpOpenid, unionid);
        requireActive(user);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toResp(user);
    }

    /** unionid 优先（跨端同一人），其次 mp_openid，最后新建。 */
    private User findOrCreateByOAuth(String mpOpenid, String unionid) {
        User user = null;
        if (unionid != null && !unionid.isBlank()) {
            user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUnionid, unionid));
        }
        if (user == null) {
            user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getMpOpenid, mpOpenid));
        }
        if (user != null) {
            user.setMpOpenid(mpOpenid);
            if (unionid != null && !unionid.isBlank()) {
                user.setUnionid(unionid);
            }
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            return user;
        }
        user = new User();
        // openid 列 NOT NULL UNIQUE：公众号用户先用 mp_ 前缀占位，
        // 之后若同一个人在小程序登录（unionid 相同），会命中这一行并补上真实 openid。
        user.setOpenid("mp_" + mpOpenid);
        user.setMpOpenid(mpOpenid);
        user.setUnionid(unionid == null || unionid.isBlank() ? null : unionid);
        user.setRole("USER");
        user.setNickname("同学" + (int) (Math.random() * 9000 + 1000));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    /** 被封禁的账号不允许登录（后台「封禁违规用户」要真的拦住入口，而不只是拦下单）。 */
    private void requireActive(User user) {
        Integer st = user.getStatus();
        if (st != null && st == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被封禁，如有疑问请联系平台客服");
        }
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
