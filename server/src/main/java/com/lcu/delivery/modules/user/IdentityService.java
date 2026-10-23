package com.lcu.delivery.modules.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.Crypto;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 校内身份核验（学号/工号）。
 *
 * 平台开关：sys_config.feature.identity_check（默认 false）。
 *  - 关闭：不限制非本校人员注册下单（一期默认）
 *  - 开启：未核验用户在提交订单时被拦截（错误码 2005）
 *
 * 说明：登录本身不拦截，避免用户在登录环节卡死；拦截点放在下单，符合
 * 「默认不影响注册下单，运营需要时一键收紧」的要求。
 */
@Service
public class IdentityService {

    /** 学号/工号：6~20 位字母或数字（兼容本科学号、研究生学号、教职工工号） */
    private static final Pattern STU_NO = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    private static final String KEY_ENABLED = "feature.identity_check";

    private final UserMapper userMapper;
    private final SysConfigService config;
    private final Crypto crypto;

    public IdentityService(UserMapper userMapper, SysConfigService config, Crypto crypto) {
        this.userMapper = userMapper;
        this.config = config;
        this.crypto = crypto;
    }

    /** 后台开关是否开启 */
    public boolean required() {
        return config.getBool(KEY_ENABLED, false);
    }

    public Map<String, Object> status(Long userId) {
        User user = userMapper.selectById(userId);
        boolean verified = user != null && user.getIdentityStatus() != null && user.getIdentityStatus() == 1;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("required", required());
        map.put("verified", verified);
        map.put("stuNoMask", verified ? mask(crypto.decrypt(user.getStuNoEnc())) : null);
        return map;
    }

    /** 提交学号/工号核验，返回脱敏后的号码 */
    @Transactional
    public String verify(Long userId, String stuNo) {
        String no = stuNo == null ? "" : stuNo.trim();
        if (!STU_NO.matcher(no).matches()) {
            throw new BizException(ErrorCode.PARAM, "学号/工号格式不正确（6~20 位字母或数字）");
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 同一个学号不允许被多个账号占用。
        // 注意：AES-GCM 每次加密随机 IV，同一明文密文不同，所以查重必须用确定性哈希。
        String hash = sha256(no);
        Long dup = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getStuNoHash, hash)
                .ne(User::getId, userId));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM, "该学号/工号已被其他账号绑定");
        }
        user.setStuNoEnc(crypto.encrypt(no));
        user.setStuNoHash(hash);
        user.setIdentityStatus(1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return mask(no);
    }

    /** 下单前置校验：开关开启且未核验 → 拦截 */
    public void requireVerified(Long userId) {
        if (!required()) {
            return;
        }
        User user = userMapper.selectById(userId);
        boolean verified = user != null && user.getIdentityStatus() != null && user.getIdentityStatus() == 1;
        if (!verified) {
            throw new BizException(ErrorCode.IDENTITY_REQUIRED, "平台已开启校内身份核验，请先在「我的」完成学号/工号核验");
        }
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(500, "计算学号摘要失败");
        }
    }

    /** 2021****1234 */
    static String mask(String no) {
        if (no == null || no.length() < 8) {
            return no;
        }
        return no.substring(0, 4) + "****" + no.substring(no.length() - 4);
    }
}
