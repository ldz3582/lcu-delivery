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
                seedRoleAccounts();
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
            seedRoleAccounts();
        } catch (Exception e) {
            log.warn("初始化管理员失败（库未就绪时可忽略）：{}", e.getMessage());
        }
    }

    /**
     * 演示多角色权限：补建 订单客服 / 财务 / 审核员 三个账号（已存在则跳过）。
     * 密码取 ADMIN_INIT_PASSWORD，否则用固定的演示密码，并在日志里提示。
     */
    private void seedRoleAccounts() {
        String pwd = System.getenv("ADMIN_INIT_PASSWORD");
        boolean demo = false;
        if (pwd == null || pwd.isBlank()) {
            pwd = "Admin@123456";
            demo = true;
        }
        String[][] seeds = {
                {"kefu", "订单客服", "CS"},
                {"caiwu", "财务", "FINANCE"},
                {"shenhe", "审核员", "AUDITOR"},
        };
        for (String[] s : seeds) {
            try {
                Long exists = adminMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                        .eq(AdminUser::getUsername, s[0]));
                if (exists != null && exists > 0) {
                    continue;
                }
                AdminUser u = new AdminUser();
                u.setUsername(s[0]);
                u.setPasswordHash(new BCryptPasswordEncoder().encode(pwd));
                u.setRealName(s[1]);
                u.setRole(s[2]);
                u.setStatus(1);
                u.setCreatedAt(LocalDateTime.now());
                adminMapper.insert(u);
                log.info("已创建后台角色账号：{}（{}）", s[0], s[2]);
            } catch (Exception e) {
                log.debug("创建角色账号 {} 跳过：{}", s[0], e.getMessage());
            }
        }
        if (demo) {
            log.warn("演示环境已创建多角色后台账号 kefu/caiwu/shenhe，密码 {}（生产请改 ADMIN_INIT_PASSWORD）", pwd);
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
