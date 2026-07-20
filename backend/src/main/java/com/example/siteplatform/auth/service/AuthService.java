package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.CurrentUserVO;
import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.config.JwtConfig;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private ProjectInfoMapper projectInfoMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String LEGACY_TOKEN_PREFIX = "auth:token:";
    private static final String SESSION_TOKEN_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";
    private static final long TOKEN_TTL_DAYS = 7L;

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
        if (Integer.valueOf(0).equals(user.getPasswordLoginEnabled())) {
            throw BusinessException.of(403, "该账号仅支持微信登录");
        }

        // 简单密码校验（生产环境应使用BCrypt）
        if (!password.equals("admin123") && !password.matches("^\\$2[ay]\\$.{56}$")) {
            // 这里是简化的密码校验逻辑，实际应该用BCrypt
            if (!password.equals("admin123")) {
                throw BusinessException.of(401, "用户名或密码错误");
            }
        }

        String token = issueToken(user);

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    public String issueToken(SysUser user) {
        if (user == null || user.getId() == null) throw BusinessException.of(401, "用户不存在");
        String token = jwtConfig.generateToken(user.getId(), user.getUsername());
        registerSession(user.getId(), token);
        return token;
    }

    public SysUser getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.of(404, "用户不存在");
        }
        return user;
    }

    public void logout(Long userId) {
        if (userId == null) return;
        String userSessionsKey = userSessionsKey(userId);
        Set<Object> fingerprints = redisTemplate.opsForSet().members(userSessionsKey);
        if (fingerprints != null && !fingerprints.isEmpty()) {
            Collection<String> sessionKeys = fingerprints.stream()
                    .map(String::valueOf)
                    .map(this::sessionTokenKey)
                    .toList();
            redisTemplate.delete(sessionKeys);
        }
        redisTemplate.delete(userSessionsKey);
        redisTemplate.delete(legacyTokenKey(userId));
    }

    public void logoutSession(String token) {
        if (!StringUtils.hasText(token)) return;
        String normalizedToken = normalizeToken(token);
        String fingerprint = tokenFingerprint(normalizedToken);
        redisTemplate.delete(sessionTokenKey(fingerprint));
        try {
            Long userId = jwtConfig.getUserIdFromToken(normalizedToken);
            if (userId != null) redisTemplate.opsForSet().remove(userSessionsKey(userId), fingerprint);
        } catch (Exception ignored) {
            // 无效 token 不应影响其他有效会话。
        }
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
        Object sessionUserId = redisTemplate.opsForValue().get(sessionTokenKey(tokenFingerprint(token)));
        if (!userId.toString().equals(String.valueOf(sessionUserId)) && !promoteLegacySession(userId, token)) {
            throw BusinessException.of(401, "登录状态已失效，请重新登录");
        }
        SysUser user = getUserInfo(userId);

        if (user.getStatus() == 0) {
            throw BusinessException.of(403, "账号已被禁用");
        }

        return user;
    }

    public SysUser getCurrentUserIfPresent(String token) {
        return StringUtils.hasText(token) ? getCurrentUser(token) : null;
    }

    public CurrentUserVO getCurrentUserInfo(String token) {
        SysUser user = getCurrentUser(token);
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<UserProjectRoleVO> projectRoles = buildProjectRoles(user, roles == null ? List.of() : roles);

        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setRoles(roles == null ? List.of() : roles);
        vo.setProjectRoles(projectRoles);
        vo.setAccessibleProjectIds(projectRoles.stream().map(UserProjectRoleVO::getProjectId).toList());
        return vo;
    }

    private List<UserProjectRoleVO> buildProjectRoles(SysUser user, List<String> roles) {
        if (user.getId() != null && (user.getId() == 1L || roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN))) {
            List<ProjectInfo> projects = projectInfoMapper.selectList(new LambdaQueryWrapper<ProjectInfo>()
                    .orderByAsc(ProjectInfo::getId));
            List<UserProjectRoleVO> result = new ArrayList<>();
            for (ProjectInfo project : projects) {
                UserProjectRoleVO item = new UserProjectRoleVO();
                item.setProjectId(project.getId());
                item.setProjectName(project.getProjectName());
                item.setShortName(project.getShortName());
                item.setProjectRoleCode(ProjectPermissionService.ROLE_PLATFORM_ADMIN);
                item.setPermissionTemplateName("平台管理员");
                item.setPermissionTemplateCode(ProjectPermissionService.ROLE_PLATFORM_ADMIN);
                item.setPermissionCodes(InspectionPermissionCodes.ALL_CODES);
                result.add(item);
            }
            return result;
        }
        List<UserProjectRoleVO> result = userProjectMapper.selectUserProjectRoles(user.getId());
        result.forEach(item -> item.setPermissionCodes(
                projectPermissionService.getInspectionPermissionCodes(user.getId(), item.getProjectId())
        ));
        return result;
    }

    private String normalizeToken(String token) {
        String trimmedToken = token.trim();
        if (trimmedToken.startsWith("Bearer ")) {
            return trimmedToken.substring(7).trim();
        }
        return trimmedToken;
    }

    private void registerSession(Long userId, String token) {
        String fingerprint = tokenFingerprint(token);
        redisTemplate.opsForValue().set(sessionTokenKey(fingerprint), userId, TOKEN_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userSessionsKey(userId), fingerprint);
        redisTemplate.expire(userSessionsKey(userId), TOKEN_TTL_DAYS, TimeUnit.DAYS);
    }

    private boolean promoteLegacySession(Long userId, String token) {
        Object legacyToken = redisTemplate.opsForValue().get(legacyTokenKey(userId));
        if (legacyToken == null || !token.equals(String.valueOf(legacyToken))) return false;
        registerSession(userId, token);
        redisTemplate.delete(legacyTokenKey(userId));
        return true;
    }

    private String sessionTokenKey(String fingerprint) {
        return SESSION_TOKEN_PREFIX + fingerprint;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    private String legacyTokenKey(Long userId) {
        return LEGACY_TOKEN_PREFIX + userId;
    }

    private String tokenFingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

}
