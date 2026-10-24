package com.lcu.delivery.modules.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcu.delivery.config.WechatProperties;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信订阅消息。
 *
 * 模板 ID 放在 sys_config（后台配置页可改）：
 *   wx.template.order_status  订单状态变化
 *   wx.template.coupon        优惠券到账/临期
 * 没配 AppID/Secret 或模板 ID 时不发真消息，只写一条 notify_log（status=2 + 原因），
 * 这样本地联调和压测不会因为微信接口不可用而卡住主流程。
 *
 * access_token 缓存在进程内（单实例够用；多实例请换 Redis，键 wx:access_token）。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    public static final String T_ORDER = "order_status";
    public static final String T_COUPON = "coupon";

    private final UserMapper userMapper;
    private final SysConfigService config;
    private final WechatProperties wechat;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private final RestClient rest = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory() {{
                setConnectTimeout(2000);
                setReadTimeout(3000);
            }})
            .build();

    private volatile String cachedToken;
    private volatile long tokenExpireAtMillis;

    public NotifyService(UserMapper userMapper, SysConfigService config, WechatProperties wechat, JdbcTemplate jdbc) {
        this.userMapper = userMapper;
        this.config = config;
        this.wechat = wechat;
        this.jdbc = jdbc;
    }

    public record Template(String key, String title, String templateId, boolean configured) {
    }

    /** 给小程序端 requestSubscribeMessage 用：没配置的模板前端直接跳过。 */
    public List<Template> templates() {
        List<Template> list = new ArrayList<>();
        list.add(new Template(T_ORDER, "订单状态通知", config.get("wx.template.order_status", ""),
                !config.get("wx.template.order_status", "").isBlank()));
        list.add(new Template(T_COUPON, "优惠券通知", config.get("wx.template.coupon", ""),
                !config.get("wx.template.coupon", "").isBlank()));
        return list;
    }

    public void notifyOrderStatus(Long userId, String orderNo, Long orderId, String statusText, String remark) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("character_string1", orderNo);
        data.put("phrase2", statusText);
        data.put("thing3", remark == null ? "点击查看订单详情" : remark);
        send(userId, T_ORDER, orderNo, "pages/order/detail?id=" + orderId, data);
    }

    public void notifyCoupon(Long userId, String couponName, String endAt) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("thing1", couponName);
        data.put("time2", endAt);
        data.put("thing3", "记得在有效期内使用哦");
        send(userId, T_COUPON, "COUPON" + System.currentTimeMillis(), "pages/coupon/index", data);
    }

    /** 真正发送：任何失败都只记日志，绝不影响下单/退款主链路。 */
    public void send(Long userId, String templateKey, String bizNo, String page, Map<String, String> data) {
        String templateId = config.get("wx.template." + templateKey, "");
        try {
            User user = userId == null ? null : userMapper.selectById(userId);
            if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
                record(userId, templateId, bizNo, 2, "用户不存在或没有 openid");
                return;
            }
            WechatProperties.Miniapp miniapp = wechat.miniapp();
            String appid = miniapp == null ? null : miniapp.appid();
            String secret = miniapp == null ? null : miniapp.secret();
            if (templateId.isBlank()) {
                record(userId, templateId, bizNo, 2, "未配置模板 ID（wx.template." + templateKey + "）");
                return;
            }
            if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
                record(userId, templateId, bizNo, 2, "模拟环境：未配置小程序 AppID/Secret，未真实发送");
                return;
            }
            String token = accessToken(appid, secret);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("touser", user.getOpenid());
            payload.put("template_id", templateId);
            payload.put("page", page);
            payload.put("miniprogram_state", "formal");
            payload.put("lang", "zh_CN");
            Map<String, Object> fields = new LinkedHashMap<>();
            data.forEach((k, v) -> fields.put(k, Map.of("value", clip(v))));
            payload.put("data", fields);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.post()
                    .uri("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            int errcode = resp == null ? -1 : Integer.parseInt(String.valueOf(resp.getOrDefault("errcode", -1)));
            if (errcode == 0) {
                record(userId, templateId, bizNo, 1, null);
            } else {
                String errmsg = resp == null ? "空响应" : String.valueOf(resp.get("errmsg"));
                record(userId, templateId, bizNo, 2, "errcode=" + errcode + " " + errmsg);
            }
        } catch (Exception e) {
            log.warn("订阅消息发送失败（不影响主流程）：{}", e.getMessage());
            record(userId, templateId, bizNo, 2, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public List<Map<String, Object>> logs(int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, target_type, target_id, template_id, biz_no, channel, status, error, created_at "
                        + "FROM notify_log ORDER BY id DESC LIMIT ? OFFSET ?", size, offset)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("targetType", r.get("target_type"));
            m.put("targetId", r.get("target_id"));
            m.put("templateId", r.get("template_id"));
            m.put("bizNo", r.get("biz_no"));
            m.put("channel", r.get("channel"));
            m.put("status", r.get("status"));
            int st = r.get("status") == null ? 0 : ((Number) r.get("status")).intValue();
            m.put("statusText", st == 1 ? "发送成功" : st == 0 ? "待发送" : "发送失败");
            m.put("error", r.get("error"));
            Object created = r.get("created_at");
            m.put("createdAt", created == null ? null
                    : (created instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toString().replace('T', ' ')
                    : String.valueOf(created).replace('T', ' ')));
            rows.add(m);
        }
        return rows;
    }

    // ---------------- 内部 ----------------

    private String accessToken(String appid, String secret) {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAtMillis) {
            return cachedToken;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + appid + "&secret=" + secret;
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = rest.get().uri(url).retrieve().body(Map.class);
        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("获取 access_token 失败：" + resp);
        }
        cachedToken = String.valueOf(resp.get("access_token"));
        long expiresIn = Long.parseLong(String.valueOf(resp.getOrDefault("expires_in", 7200)));
        tokenExpireAtMillis = now + Math.max(60, expiresIn - 300) * 1000;
        return cachedToken;
    }

    private void record(Long userId, String templateId, String bizNo, int status, String error) {
        try {
            jdbc.update("INSERT INTO notify_log (target_type, target_id, template_id, biz_no, channel, status, error, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    1, userId == null ? 0 : userId,
                    templateId == null || templateId.isBlank() ? null : templateId,
                    bizNo == null ? "" : bizNo, "SUBSCRIBE", status, error, LocalDateTime.now());
        } catch (Exception e) {
            log.debug("写 notify_log 失败：{}", e.getMessage());
        }
    }

    /** 微信 thing 类字段最多 20 个字符，超了整条消息会报错。 */
    private String clip(String v) {
        if (v == null) {
            return "";
        }
        return v.length() > 20 ? v.substring(0, 19) + "…" : v;
    }
}
