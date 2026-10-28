package com.lcu.delivery.modules.user;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.Crypto;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.user.entity.User;
import com.lcu.delivery.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final Crypto crypto;

    public UserService(UserMapper userMapper, Crypto crypto) {
        this.userMapper = userMapper;
        this.crypto = crypto;
    }

    public User require(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || (user.getDeletedAt() != null)) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /** 手机号：展示用掩码 + 落库用 AES/GCM 密文（需求 9.4 手机号加密存储）。 */
    public void bindPhone(Long userId, String phone) {
        User user = require(userId);
        user.setPhoneMask(Crypto.maskPhone(phone));
        user.setPhoneEnc(crypto.encrypt(phone));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void updateProfile(Long userId, String nickname, String avatar) {
        User user = require(userId);
        if (nickname != null && !nickname.isBlank()) {
            String n = nickname.trim();
            if (n.length() > 32) {
                throw new BizException(ErrorCode.PARAM, "昵称最多 32 个字符");
            }
            user.setNickname(n);
        }
        if (avatar != null) {
            if (avatar.length() > 512) {
                throw new BizException(ErrorCode.PARAM, "头像地址过长");
            }
            user.setAvatar(avatar);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void upgradeRole(Long userId, String role) {
        User user = require(userId);
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
