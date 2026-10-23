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

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User require(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || (user.getDeletedAt() != null)) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public void bindPhone(Long userId, String phone) {
        User user = require(userId);
        user.setPhoneMask(Crypto.maskPhone(phone));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void updateProfile(Long userId, String nickname, String avatar) {
        User user = require(userId);
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
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
