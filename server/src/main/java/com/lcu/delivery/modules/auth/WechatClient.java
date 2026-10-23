package com.lcu.delivery.modules.auth;

import com.lcu.delivery.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信小程序登录：code -> openid。
 * 未配置 AppID 时走本地 mock（见 AuthService），这里只负责真实请求。
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
}
