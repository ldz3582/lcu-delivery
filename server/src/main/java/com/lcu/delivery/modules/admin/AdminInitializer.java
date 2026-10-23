package com.lcu.delivery.modules.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.modules.admin.entity.AdminUser;
import com.lcu.delivery.modules.admin.mapper.AdminUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 首次启动创建管理员，不把密码哈希写进 SQL。
 * 密码优先取环境变量 ADMIN_INIT_PASSWORD，否则随机生成并打印到日志（仅一次）。
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminUserMapper adminMapper;

    public AdminInitializer(AdminUserMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Override
    public void run(String... args) {
        try {
            Long count = adminMapper.selectCount(Wrappers.<AdminUser>lambdaQuery());
            if (count != null && count > 0) {
                return;
            }
            String pwd = System.getenv("ADMIN_INIT_PASSWORD");
            if (pwd == null || pwd.isBlank()) {
                pwd = randomPassword();
            }
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPasswordHash(new BCryptPasswordEncoder().encode(pwd));
            admin.setRealName("超级管理员");
            admin.setRole("SUPER");
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            adminMapper.insert(admin);
            log.warn("=================================================================");
            log.warn("已创建初始管理员：admin / {}", pwd);
            log.warn("登录后请立即修改密码；生产环境用 ADMIN_INIT_PASSWORD 指定");
            log.warn("=================================================================");
        } catch (Exception e) {
            log.warn("初始化管理员失败（库未就绪时可忽略）：{}", e.getMessage());
        }
    }

    private String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
