package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.CurrentUserVO;
import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.config.JwtConfig;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.service.SystemPermissionService;
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
import java.util.LinkedHashSet;
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

    @Autowired
    private PasswordCredentialService passwordCredentialService;

    @Autowired
    private SystemPermissionService systemPermissionService;

    @Autowired
    private SysUserWechatBindingMapper wechatBindingMapper;

    private static final String LEGACY_TOKEN_PREFIX = "auth:token:";
    private static final String SESSION_TOKEN_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";
    /**
     * 不存在账号或历史非 BCrypt 凭证也执行同成本校验，避免通过响应耗时枚举账号。
     * 该摘要仅用于占位，任何情况下都不会对应到真实用户。
     */
    private static final String DUMMY_BCRYPT =
            "$2y$12$zF276taNiyLyh5qo1lXw3.JsxS2/6jaAB1Brlh2fndoS72lE4zI2y";

    public LoginResponse login(LoginRequest request) {
        SysUser user = findLoginUser(request);
        String token = issueToken(user);

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    public String issueToken(SysUser user) {
        if (user == null || user.getId() == null) throw BusinessException.of(401, "用户不存在");
        String token = jwtConfig.generateToken(user.getId(), user.getUsername(), normalizedCredentialVersion(user));
        registerSession(user.getId(), token);
        return token;
    }

    public SysUser authenticateCredentials(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return findLoginUser(request);
    }

    private SysUser findLoginUser(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw BusinessException.of(400, "用户名或密码不能为空");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .last("LIMIT 1"));
        boolean hasBcryptCredential = user != null && passwordCredentialService.isBcrypt(user.getPassword());
        String passwordHash = hasBcryptCredential ? user.getPassword() : DUMMY_BCRYPT;
        boolean passwordMatches = passwordCredentialService.matches(request.getPassword(), passwordHash);
        if (user == null || !hasBcryptCredential || !passwordMatches) {
            throw BusinessException.of(401, "用户名或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) throw BusinessException.of(403, "账号已被禁用");
        if (Integer.valueOf(0).equals(user.getPasswordLoginEnabled())) {
            throw BusinessException.of(403, "该账号仅支持微信登录");
        }
        if (Integer.valueOf(1).equals(user.getPasswordResetRequired())) {
            throw BusinessException.of(403, "账号密码需要由管理员重置后才能登录");
        }
        return user;
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
        Integer tokenCredentialVersion = jwtConfig.getCredentialVersionFromToken(token);
        if (tokenCredentialVersion == null
                || !tokenCredentialVersion.equals(normalizedCredentialVersion(user))) {
            logoutSession(token);
            throw BusinessException.of(401, "登录凭证已更新，请重新登录");
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
        vo.setPasswordLoginEnabled(user.getPasswordLoginEnabled());
        SysUserWechatBinding binding = wechatBindingMapper.selectOne(
                new LambdaQueryWrapper<SysUserWechatBinding>()
                        .eq(SysUserWechatBinding::getUserId, user.getId())
                        .orderByDesc(SysUserWechatBinding::getId)
                        .last("LIMIT 1"));
        vo.setWechatBound(binding != null && "ACTIVE".equals(binding.getStatus()));
        vo.setWechatBindingStatus(binding == null ? "UNBOUND" : binding.getStatus());
        vo.setRoles(roles == null ? List.of() : roles);
        vo.setProjectRoles(projectRoles);
        vo.setProjectContexts(projectRoles);
        vo.setAccessibleProjectIds(projectRoles.stream().map(UserProjectRoleVO::getProjectId).toList());
        vo.setPermissionCodes(systemPermissionService.permissionCodes(user.getId()));
        vo.setMenus(systemPermissionService.menuTree(user.getId()));
        return vo;
    }

    private List<UserProjectRoleVO> buildProjectRoles(SysUser user, List<String> roles) {
        if (user.getId() != null && roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN)) {
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
        result.forEach(item -> {
            LinkedHashSet<String> codes = new LinkedHashSet<>(
                    projectPermissionService.getInspectionPermissionCodes(user.getId(), item.getProjectId()));
            codes.addAll(systemPermissionService.projectRolePermissionCodes(item.getProjectRoleCode()));
            item.setPermissionCodes(List.copyOf(codes));
        });
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
        long ttlMillis = Math.max(jwtConfig.getExpirationMillis(), 60_000L);
        redisTemplate.opsForValue().set(sessionTokenKey(fingerprint), userId, ttlMillis, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(userSessionsKey(userId), fingerprint);
        redisTemplate.expire(userSessionsKey(userId), ttlMillis, TimeUnit.MILLISECONDS);
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

    public String hashPassword(String rawPassword) {
        return passwordCredentialService.encode(rawPassword);
    }

    public void changePassword(SysUser user, String newPassword) {
        user.setPassword(hashPassword(newPassword));
        user.setCredentialVersion(normalizedCredentialVersion(user) + 1);
        user.setPasswordResetRequired(0);
        user.setPasswordLoginEnabled(1);
        userMapper.updateById(user);
        logout(user.getId());
    }

    private int normalizedCredentialVersion(SysUser user) {
        return user.getCredentialVersion() == null || user.getCredentialVersion() < 1
                ? 1 : user.getCredentialVersion();
    }

}
