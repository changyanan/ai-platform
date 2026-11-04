package com.aiplatform.server.user.config;

import com.aiplatform.server.config.BootstrapProperties;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 初始化管理员账号；demo 账号默认禁用，仅在显式开启时创建/启用。
 * 数据库只保存 BCrypt 哈希，初始密码必须首次登录后修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private final BootstrapProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("账号初始化已关闭");
            return;
        }
        createIfMissing(properties.getAdminUsername(), properties.getAdminPassword(), "平台管理员", "ADMIN");
        if (properties.isDemoEnabled()) {
            createIfMissing(properties.getDemoUsername(), properties.getDemoPassword(), "演示用户", "USER");
        } else {
            disableExistingDemoAccount();
        }

        if (System.getenv("AI_PLATFORM_JWT_SECRET") == null) {
            log.warn("正在使用内置 JWT 密钥，生产部署必须设置 AI_PLATFORM_JWT_SECRET");
        }
        log.info("管理员账号已就绪；demo 账号默认{}。首次登录必须修改初始密码。",
                properties.isDemoEnabled() ? "启用" : "禁用");
    }

    private void disableExistingDemoAccount() {
        if (!StringUtils.hasText(properties.getDemoUsername())) {
            return;
        }
        String normalized = properties.getDemoUsername().trim().toLowerCase(Locale.ROOT);
        User demo = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, normalized)
                .last("LIMIT 1"));
        if (demo != null && Boolean.TRUE.equals(demo.getEnabled())) {
            demo.setEnabled(false);
            demo.setTokenVersion(demo.getTokenVersion() == null ? 1 : demo.getTokenVersion() + 1);
            userMapper.updateById(demo);
            log.info("演示账号 '{}' 已默认禁用", normalized);
        }
    }

    private void createIfMissing(String username, String rawPassword, String displayName, String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            log.warn("跳过空的演示账号配置：role={}", role);
            return;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, normalized));
        if (exists > 0) {
            return;
        }
        if (rawPassword.length() < 8) {
            throw new IllegalStateException("演示账号密码至少需要 8 位：" + normalized);
        }
        User user = new User();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEmail(null);
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setTokenVersion(0);
        userMapper.insert(user);
        log.info("Initialized demo account '{}' with role {}", normalized, role);
    }
}
