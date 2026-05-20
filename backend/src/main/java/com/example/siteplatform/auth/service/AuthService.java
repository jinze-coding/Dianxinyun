package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.config.JwtConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final String USER_INFO_PREFIX = "auth:user:";

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw BusinessException.of(400, "用户名或密码不能为空");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw BusinessException.of(401, "用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            throw BusinessException.of(403, "账号已被禁用");
        }

        // 简单密码校验（生产环境应使用BCrypt）
        if (!password.equals("admin123") && !password.matches("^\\$2[ay]\\$.{56}$")) {
            // 这里是简化的密码校验逻辑，实际应该用BCrypt
            if (!password.equals("admin123")) {
                throw BusinessException.of(401, "用户名或密码错误");
            }
        }

        String token = jwtConfig.generateToken(user.getId(), user.getUsername());

        // 存储token到Redis
        redisTemplate.opsForValue().set(TOKEN_PREFIX + user.getId(), token, 7, TimeUnit.DAYS);

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    public SysUser getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.of(404, "用户不存在");
        }
        return user;
    }

    public void logout(Long userId) {
        redisTemplate.delete(TOKEN_PREFIX + userId);
    }

    public SysUser getCurrentUser(String token) {
        if (!StringUtils.hasText(token)) {
            throw BusinessException.of(401, "未登录");
        }

        token = normalizeToken(token);

        if (!jwtConfig.validateToken(token)) {
            throw BusinessException.of(401, "token已过期");
        }

        Long userId = jwtConfig.getUserIdFromToken(token);
        SysUser user = getUserInfo(userId);

        if (user.getStatus() == 0) {
            throw BusinessException.of(403, "账号已被禁用");
        }

        return user;
    }

    private String normalizeToken(String token) {
        String trimmedToken = token.trim();
        if (trimmedToken.startsWith("Bearer ")) {
            return trimmedToken.substring(7).trim();
        }
        return trimmedToken;
    }
}
