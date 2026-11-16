package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.pay.PayConfigService;
import com.lcu.delivery.modules.pay.PayGatewayProvider;
import com.lcu.delivery.modules.pay.PayoutGatewayProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台「支付配置」：可视化配置微信支付，带状态诊断、连通性测试与分步引导。
 *
 * <p>安全约定：
 * <ul>
 *   <li>读取接口只返回脱敏值（商户号留前 4 位、密钥只回显「已配置」）</li>
 *   <li>保存时敏感字段留空 = 不修改，避免前端把脱敏串回写覆盖真密钥</li>
 *   <li>密钥在库里是 AES-GCM 加密的，解密只发生在服务端内存</li>
 *   <li>只允许 SUPER 角色操作</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/pay-config")
public class AdminPayConfigController {

    private final PayConfigService configService;
    private final PayGatewayProvider payProvider;
    private final PayoutGatewayProvider payoutProvider;

    public AdminPayConfigController(PayConfigService configService, PayGatewayProvider payProvider,
                                    PayoutGatewayProvider payoutProvider) {
        this.configService = configService;
        this.payProvider = payProvider;
        this.payoutProvider = payoutProvider;
    }

    /** 当前状态 + 缺失项 + 引导步骤。 */
    @GetMapping
    public Result<Map<String, Object>> status() {
        return Result.ok(withRuntime(configService.status()));
    }

    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody PayConfigService.SaveReq req) {
        configService.save(req, UserContext.userId());
        // 配置指纹变了，下一次调用会重建客户端；主动失效让状态页立刻反映
        payProvider.invalidate();
        payoutProvider.invalidate();
        return Result.ok(withRuntime(configService.status()));
    }

    /** 库里的配置状态 + 运行时通道信息（GET/PUT 共用，避免字段不一致）。 */
    private Map<String, Object> withRuntime(Map<String, Object> base) {
        Map<String, Object> m = new LinkedHashMap<>(base);
        m.put("activeChannel", payProvider.activeChannel());
        m.put("payoutChannel", payoutProvider.mock() ? "mock" : "wechat-transfer");
        m.put("steps", steps());
        return m;
    }

    /**
     * 连通性测试：用当前配置真的去连一次微信（下载平台证书）。
     * 成功说明商户号、证书序列号、私钥、APIv3 密钥、出网都通了。
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> test() {
        Map<String, Object> m = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            String msg = payProvider.probe();
            m.put("ok", true);
            m.put("message", msg);
            m.put("channel", payProvider.activeChannel());
        } catch (BizException e) {
            m.put("ok", false);
            m.put("message", e.getMessage());
        } catch (Exception e) {
            m.put("ok", false);
            m.put("message", "测试失败：" + e.getMessage());
        }
        m.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.ok(m);
    }

    /** 引导步骤：后台页面直接渲染这份清单，不把业务说明写死在前端。 */
    private java.util.List<Map<String, Object>> steps() {
        var list = new java.util.ArrayList<Map<String, Object>>();
        list.add(step(1, "办商户号", "用营业执照在 pay.weixin.qq.com 申请微信支付商户号（个体户或企业）", false));
        list.add(step(2, "关联小程序", "商户平台 → 产品中心 → AppID 账号管理 → 关联小程序 AppID，再到小程序后台确认", false));
        list.add(step(3, "开通支付产品", "按需要开通：JSAPI 支付（小程序/公众号）、H5 支付、Native 支付、商家转账", false));
        list.add(step(4, "下载 API 证书", "商户平台 → 账户中心 → API 安全 → 申请证书，得到 apiclient_key.pem 与证书序列号", false));
        list.add(step(5, "设置 APIv3 密钥", "同一页面设置 32 位 APIv3 密钥，用于回调解密", false));
        list.add(step(6, "填本页并测试", "把上面拿到的 5 项填进表单，点「测试连通性」，成功后再把模式切到 real", false));
        list.add(step(7, "配置回调域名", "回调地址必须是公网可达的 https；小程序后台要配 request 合法域名", false));
        return list;
    }

    private static Map<String, Object> step(int no, String title, String detail, boolean done) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("no", no);
        m.put("title", title);
        m.put("detail", detail);
        m.put("done", done);
        return m;
    }
}
