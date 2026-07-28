package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Emergency, explicit administrator password reset.
 * Run once with ADMIN_RESET_USERNAME and ADMIN_RESET_PASSWORD, then remove both variables.
 */
@Component
public class AdminPasswordResetRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminPasswordResetRunner.class);

    private final SysUserMapper userMapper;
    private final AuthService authService;
    private final PasswordCredentialService passwordCredentialService;
    private final String username;
    private final String password;

    public AdminPasswordResetRunner(SysUserMapper userMapper, AuthService authService,
                                    PasswordCredentialService passwordCredentialService,
                                    @Value("${ADMIN_RESET_USERNAME:}") String username,
                                    @Value("${ADMIN_RESET_PASSWORD:}") String password) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.passwordCredentialService = passwordCredentialService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (StringUtils.hasText(username) || StringUtils.hasText(password)) {
            resetAdministratorPassword();
        }
        boolean validAdministrator = userMapper.selectActivePlatformAdministrators().stream()
                .anyMatch(user -> Integer.valueOf(1).equals(user.getPasswordLoginEnabled())
                        && !Integer.valueOf(1).equals(user.getPasswordResetRequired())
                        && passwordCredentialService.isBcrypt(user.getPassword()));
        if (!validAdministrator) {
            throw new IllegalStateException(
                    "系统至少需要一个已启用且拥有有效 BCrypt 密码的平台管理员；请先执行增量迁移并使用 ADMIN_RESET_* 显式重置");
        }
    }

    private void resetAdministratorPassword() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("管理员密码重置必须同时提供 ADMIN_RESET_USERNAME 和 ADMIN_RESET_PASSWORD");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim()).last("LIMIT 1"));
        if (user == null) throw new IllegalStateException("待重置管理员账号不存在");
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null || !roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN)) {
            throw new IllegalStateException("仅允许通过启动参数重置平台管理员密码");
        }
        authService.changePassword(user, password);
        log.warn("已显式重置平台管理员 {} 的密码并注销全部会话，请立即移除 ADMIN_RESET_* 环境变量", username);
    }
}
