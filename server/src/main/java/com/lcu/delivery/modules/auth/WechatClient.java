package com.lcu.delivery.modules.auth;

import com.lcu.delivery.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微信开放能力客户端：小程序 code2session、公众号网页授权。
 * 未配置对应 AppID 时由上层走 mock（见 AuthService），这里只负责真实请求。
 */
@Component
public class WechatClient {

    private static final Logger log = LoggerFactory.getLogger(WechatClient.class);
    private final RestClient restClient = RestClient.create();

    @SuppressWarnings("unchecked")
    public String code2openid(String appid, String secret, String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appid + "&secret=" + secret
                + "&js_code=" + code + "&grant_type=authorization_code";
        try {
            Map<String, Object> resp = restClient.get().uri(url).retrieve().body(Map.class);
            if (resp == null || resp.get("openid") == null) {
                log.warn("code2session 失败: {}", resp);
                throw new BizException(401, "微信登录失败：" + (resp == null ? "空响应" : resp.get("errmsg")));
            }
            return String.valueOf(resp.get("openid"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信登录接口异常", e);
            throw new BizException(401, "微信登录服务不可用");
        }
    }

    /** 公众号网页授权 URL（snsapi_base 静默授权，用户无感知，只拿 openid）。 */
    public String officialAuthUrl(String appid, String redirectUri, String state) {
        String enc = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + appid
                + "&redirect_uri=" + enc
                + "&response_type=code&scope=snsapi_base"
                + "&state=" + (state == null ? "lcu" : state)
                + "#wechat_redirect";
    }

    /** 公众号网页授权的 code 换 openid/unionid。 */
    public record OAuthUser(String openid, String unionid) {
    }

    @SuppressWarnings("unchecked")
    public OAuthUser officialOpenid(String appid, String secret, String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + appid + "&secret=" + secret
                + "&code=" + code + "&grant_type=authorization_code";
        try {
            Map<String, Object> resp = restClient.get().uri(url).retrieve().body(Map.class);
            if (resp == null || resp.get("openid") == null) {
                log.warn("公众号网页授权失败: {}", resp);
                throw new BizException(401, "公众号登录失败："
                        + (resp == null ? "空响应" : resp.get("errmsg")));
            }
            Object unionid = resp.get("unionid");
            return new OAuthUser(String.valueOf(resp.get("openid")),
                    unionid == null ? null : String.valueOf(unionid));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用公众号授权接口异常", e);
            throw new BizException(401, "公众号授权服务不可用");
        }
    }
}
