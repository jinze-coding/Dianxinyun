package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.WechatPhoneRequest;
import com.example.siteplatform.auth.dto.WechatSessionRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
import com.example.siteplatform.auth.dto.WechatBindLoginRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WechatAuthService {

    private static final String SESSION_PREFIX = "wechat:login-session:";
    private static final String SESSION_CONSUMED_PREFIX = "wechat:login-session-consumed:";
    private final WechatPlatformClient platformClient;
    private final SysUserWechatBindingMapper bindingMapper;
    private final WechatAccessApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final ElectricBoxMapper electricBoxMapper;
    private final ProjectPermissionService permissionService;
    private final AuthService authService;
    private final RedisTemplate<String, Object> redisTemplate;

    public WechatAuthService(WechatPlatformClient platformClient,
                             SysUserWechatBindingMapper bindingMapper,
                             WechatAccessApplicationMapper applicationMapper,
                             SysUserMapper userMapper,
                             ElectricBoxMapper electricBoxMapper,
                             ProjectPermissionService permissionService,
                             AuthService authService,
                             RedisTemplate<String, Object> redisTemplate) {
        this.platformClient = platformClient;
        this.bindingMapper = bindingMapper;
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.electricBoxMapper = electricBoxMapper;
        this.permissionService = permissionService;
        this.authService = authService;
        this.redisTemplate = redisTemplate;
    }

    public WechatSessionResponse session(WechatSessionRequest request) {
        if (request == null) throw new BusinessException("微信登录参数不能为空");
        WechatPlatformClient.WechatIdentity identity = platformClient.login(request.getCode());
        SceneContext scene = resolveScene(request.getScene());
        SysUserWechatBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, identity.appId())
                .eq(SysUserWechatBinding::getOpenid, identity.openid())
                .last("LIMIT 1"));
        if (binding != null && "DISABLED".equals(binding.getStatus())) {
            WechatSessionResponse response = new WechatSessionResponse();
            response.setBindingStatus("BINDING_DISABLED"); response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
            response.setMessage("微信登录已被停用，请联系平台管理员"); return response;
        }
        if (binding != null && "ACTIVE".equals(binding.getStatus())) {
            SysUser user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw BusinessException.forbidden("微信绑定的系统账号不存在，请联系平台管理员");
            }
            if (!Integer.valueOf(1).equals(user.getStatus())) {
                throw BusinessException.forbidden("账号已被禁用");
            }
            binding.setLastLoginTime(LocalDateTime.now());
            bindingMapper.updateById(binding);
            return authorizedResponse(user, scene);
        }
        WechatAccessApplication latestApplication = scene.projectId() == null ? null : applicationMapper.selectOne(
                new LambdaQueryWrapper<WechatAccessApplication>()
                        .eq(WechatAccessApplication::getAppId, identity.appId())
                        .eq(WechatAccessApplication::getOpenid, identity.openid())
                        .eq(WechatAccessApplication::getProjectId, scene.projectId())
                        .orderByDesc(WechatAccessApplication::getId).last("LIMIT 1"));
        if (latestApplication != null && "PENDING".equals(latestApplication.getStatus())) {
            WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PENDING_APPROVAL");
            response.setApplicationStatus("PENDING"); response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
            response.setMessage("内部人员申请正在审批中"); return response;
        }
        String sessionToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForHash().putAll(SESSION_PREFIX + sessionToken, Map.of(
                "appId", identity.appId(), "openid", identity.openid(),
                "unionid", identity.unionid() == null ? "" : identity.unionid()));
        redisTemplate.expire(SESSION_PREFIX + sessionToken, 10, TimeUnit.MINUTES);
        WechatSessionResponse response = new WechatSessionResponse();
        response.setBindingStatus(latestApplication != null && "REJECTED".equals(latestApplication.getStatus())
                ? "APPLICATION_REJECTED" : "UNBOUND");
        response.setApplicationStatus(latestApplication == null ? null : latestApplication.getStatus());
        response.setWechatSessionToken(sessionToken);
        response.setProjectId(scene.projectId());
        response.setSourceId(scene.sourceId());
        response.setMessage(latestApplication != null && "REJECTED".equals(latestApplication.getStatus())
                ? "上次申请已被拒绝：" + (StringUtils.hasText(latestApplication.getReviewComment()) ? latestApplication.getReviewComment() : "请重新核对资料")
                : "微信尚未绑定内部账号，可查看公开月表或授权手机号申请内部权限");
        return response;
    }

    public WechatSessionResponse miniLogin(WechatSessionRequest request) {
        return session(request);
    }

    @Transactional
    public WechatSessionResponse bindLogin(WechatBindLoginRequest request) {
        if (request == null) throw new BusinessException("微信绑定参数不能为空");
        SysUser user = authService.authenticateCredentials(request.getUsername(), request.getPassword());
        WechatPlatformClient.WechatIdentity identity = platformClient.login(request.getCode());
        bind(user, identity.appId(), identity.openid(), identity.unionid(), user.getPhone());
        return authorizedResponse(user, new SceneContext(null, null));
    }

    @Transactional
    public WechatSessionResponse bindCurrent(String code, SysUser user) {
        WechatPlatformClient.WechatIdentity identity = platformClient.login(code);
        bind(user, identity.appId(), identity.openid(), identity.unionid(), user.getPhone());
        return authorizedResponse(user, new SceneContext(null, null));
    }

    @Transactional
    public void unbindCurrent(SysUser user, String password) {
        if (!Integer.valueOf(1).equals(user.getPasswordLoginEnabled())) {
            throw BusinessException.forbidden("仅微信登录账号请联系平台管理员解绑");
        }
        authService.authenticateCredentials(user.getUsername(), password);
        SysUserWechatBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, user.getId())
                .eq(SysUserWechatBinding::getAppId, platformClient.appId())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                .last("LIMIT 1 FOR UPDATE"));
        if (binding == null) throw BusinessException.notFound("当前账号没有有效微信绑定");
        binding.setStatus("UNBOUND");
        binding.setUpdateTime(LocalDateTime.now());
        if (bindingMapper.updateById(binding) != 1) {
            throw conflict("微信绑定状态已变化，请刷新后重试");
        }
        authService.logout(user.getId());
        authService.repeatLogoutAfterCommit(user.getId());
    }

    @Transactional
    public WechatSessionResponse bindPhone(WechatPhoneRequest request) {
        if (request == null || !StringUtils.hasText(request.getWechatSessionToken())) {
            throw new BusinessException("微信登录会话已失效，请重新登录");
        }
        String key = SESSION_PREFIX + request.getWechatSessionToken().trim();
        Map<Object, Object> session = redisTemplate.opsForHash().entries(key);
        if (session.isEmpty()) throw new BusinessException("微信登录会话已失效，请重新登录");
        String appId = String.valueOf(session.get("appId"));
        String openid = String.valueOf(session.get("openid"));
        requireIdentity(appId, openid);
        String phone = normalizePhone(platformClient.getPhoneNumber(request.getPhoneCode(), request.getPhone()));
        SceneContext scene = resolveScene(request.getScene());
        if (scene.projectId() == null) throw new BusinessException("请从电箱巡检码进入后再申请项目权限");
        SysUserWechatBinding historicalBinding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, appId).eq(SysUserWechatBinding::getOpenid, openid).last("LIMIT 1"));
        if (historicalBinding != null && "DISABLED".equals(historicalBinding.getStatus())) {
            throw BusinessException.forbidden("微信登录已被停用，请联系平台管理员");
        }
        List<SysUser> matched = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getStatus, 1));
        if (matched.size() == 1 && "DISABLED".equals(permissionService.getProjectAccessStatus(matched.get(0).getId(), scene.projectId()))) {
            WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PROJECT_ACCESS_DISABLED");
            response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
            response.setMessage("当前项目访问已暂停，请联系项目管理员"); return response;
        }
        WechatSessionResponse response = new WechatSessionResponse();
        response.setBindingStatus(matched.isEmpty() ? "REGISTRATION_REQUIRED" : "BIND_ACCOUNT_REQUIRED");
        response.setProjectId(scene.projectId());
        response.setSourceId(scene.sourceId());
        response.setWechatSessionToken(request.getWechatSessionToken().trim());
        response.setMessage(matched.isEmpty()
                ? "未匹配到平台账号，请提交统一账号注册申请"
                : "手机号不能作为自动绑定凭证，请使用账号密码完成微信绑定");
        return response;
    }

    @Transactional
    public WechatSessionResponse requestProjectAccess(WechatProjectAccessRequest request, SysUser user) {
        if (request == null || !StringUtils.hasText(request.getScene())) throw new BusinessException("巡检场景码不能为空");
        SceneContext scene = resolveScene(request.getScene());
        if (scene.projectId() == null) throw new BusinessException("巡检码无效");
        String accessStatus = permissionService.getProjectAccessStatus(user.getId(), scene.projectId());
        if ("DISABLED".equals(accessStatus)) {
            WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PROJECT_ACCESS_DISABLED");
            response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId()); response.setMessage("当前项目访问已暂停，请联系项目管理员"); return response;
        }
        if (permissionService.getInspectionPermissionCodes(user.getId(), scene.projectId()).size() > 0) {
            return authorizedResponse(user, scene);
        }
        SysUserWechatBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, user.getId())
                .eq(SysUserWechatBinding::getAppId, platformClient.appId())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE").last("LIMIT 1"));
        if (binding == null) throw new BusinessException("当前账号没有有效微信绑定");
        WechatAccessApplication application = findPending(binding.getAppId(), binding.getOpenid(), scene.projectId());
        if (application == null) application = new WechatAccessApplication();
        application.setAppId(binding.getAppId()); application.setOpenid(binding.getOpenid()); application.setPhone(binding.getPhone());
        application.setRealName(user.getRealName()); application.setProjectId(scene.projectId()); application.setSourceType("ELECTRIC_BOX");
        application.setSourceId(scene.sourceId()); application.setMatchedUserId(user.getId()); application.setStatus("PENDING");
        application.setUpdateTime(LocalDateTime.now());
        if (application.getId() == null) {
            application.setCreateTime(LocalDateTime.now());
            try {
                if (applicationMapper.insert(application) != 1) {
                    throw conflict("项目访问申请提交失败，请稍后重试");
                }
            } catch (DuplicateKeyException exception) {
                application = findPending(binding.getAppId(), binding.getOpenid(), scene.projectId());
                if (application == null) {
                    throw conflict("项目访问申请状态已变化，请重新扫码");
                }
            }
        } else if (applicationMapper.updateById(application) != 1) {
            throw conflict("项目访问申请状态已变化，请重新扫码");
        }
        WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PENDING_APPROVAL");
        response.setApplicationStatus("PENDING"); response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
        response.setMessage("当前项目权限申请已提交，请等待管理员审批"); return response;
    }

    public void bind(SysUser user, String appId, String openid, String unionid, String phone) {
        if (user == null || user.getId() == null) throw BusinessException.notFound("待绑定账号不存在");
        if (!Integer.valueOf(1).equals(user.getStatus())) throw BusinessException.forbidden("账号已被禁用");
        requireIdentity(appId, openid);
        if (hasOtherActiveBinding(user.getId(), appId, openid)) {
            throw conflict("该系统账号已绑定其他微信，请先在后台解绑");
        }
        SysUserWechatBinding openidConflict = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, appId)
                .eq(SysUserWechatBinding::getOpenid, openid)
                .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (openidConflict != null && !user.getId().equals(openidConflict.getUserId())) {
            throw conflict("该微信已绑定其他系统账号");
        }
        if (StringUtils.hasText(unionid)) {
            SysUserWechatBinding unionidConflict = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                    .eq(SysUserWechatBinding::getAppId, appId)
                    .eq(SysUserWechatBinding::getUnionid, unionid)
                    .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                    .last("LIMIT 1"));
            if (unionidConflict != null && !user.getId().equals(unionidConflict.getUserId())) {
                throw conflict("该微信 UnionID 已绑定其他系统账号");
            }
        }
        SysUserWechatBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, appId).eq(SysUserWechatBinding::getOpenid, openid).last("LIMIT 1"));
        if (binding != null && "DISABLED".equals(binding.getStatus())) {
            throw BusinessException.forbidden("该微信绑定已被平台管理员停用");
        }
        boolean create = binding == null;
        if (create) binding = new SysUserWechatBinding();
        binding.setUserId(user.getId());
        binding.setAppId(appId);
        binding.setOpenid(openid);
        if (StringUtils.hasText(unionid) || create) {
            binding.setUnionid(StringUtils.hasText(unionid) ? unionid : null);
        }
        binding.setPhone(phone);
        binding.setStatus("ACTIVE");
        binding.setDeleted(0);
        binding.setBindTime(LocalDateTime.now());
        binding.setLastLoginTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        try {
            int affected;
            if (create) {
                binding.setCreateTime(LocalDateTime.now());
                affected = bindingMapper.insert(binding);
            } else {
                affected = bindingMapper.updateById(binding);
            }
            if (affected != 1) throw conflict("微信绑定状态已变化，请刷新后重试");
        } catch (DuplicateKeyException exception) {
            throw conflict("微信或系统账号已被绑定，请刷新后重试");
        }
    }

    public PendingWechatIdentity pendingIdentity(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) throw new BusinessException("微信登录会话不能为空");
        Map<Object, Object> session = redisTemplate.opsForHash().entries(SESSION_PREFIX + sessionToken.trim());
        if (session.isEmpty()) throw new BusinessException("微信登录会话已失效，请重新登录");
        String appId = String.valueOf(session.get("appId"));
        String openid = String.valueOf(session.get("openid"));
        requireIdentity(appId, openid);
        return new PendingWechatIdentity(
                appId,
                openid,
                emptyToNull(String.valueOf(session.get("unionid"))));
    }

    /**
     * 注册申请只能消费一次微信登录会话。占用标记使用 Redis SET NX，确保并发请求中
     * 只有一个请求可以继续写入申请记录。
     */
    public PendingWechatIdentity consumePendingIdentity(String sessionToken) {
        PendingWechatIdentity identity = pendingIdentity(sessionToken);
        String normalizedToken = sessionToken.trim();
        String sessionKey = SESSION_PREFIX + normalizedToken;
        String consumedKey = SESSION_CONSUMED_PREFIX + normalizedToken;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                consumedKey, "1", 10, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(claimed)) {
            throw conflict("微信登录会话已使用，请重新登录");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        restorePendingIdentity(sessionKey, consumedKey, identity);
                    } else if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        redisTemplate.delete(consumedKey);
                    }
                }
            });
        }
        redisTemplate.delete(sessionKey);
        return identity;
    }

    private void restorePendingIdentity(String sessionKey, String consumedKey, PendingWechatIdentity identity) {
        redisTemplate.opsForHash().putAll(sessionKey, Map.of(
                "appId", identity.appId(),
                "openid", identity.openid(),
                "unionid", identity.unionid() == null ? "" : identity.unionid()));
        redisTemplate.expire(sessionKey, 10, TimeUnit.MINUTES);
        redisTemplate.delete(consumedKey);
    }

    public PendingWechatIdentity identityForCode(String code) {
        WechatPlatformClient.WechatIdentity identity = platformClient.login(code);
        requireIdentity(identity.appId(), identity.openid());
        return new PendingWechatIdentity(identity.appId(), identity.openid(), identity.unionid());
    }

    public String resolvePhone(String phoneCode, String manualPhone) {
        return platformClient.getPhoneNumber(phoneCode, manualPhone);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) && !"null".equals(value) ? value : null;
    }

    private String normalizePhone(String phone) {
        String normalized = StringUtils.hasText(phone) ? phone.trim() : null;
        if (normalized == null || !normalized.matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        return normalized;
    }

    private void requireIdentity(String appId, String openid) {
        if (!StringUtils.hasText(appId) || "null".equals(appId)
                || !StringUtils.hasText(openid) || "null".equals(openid)) {
            throw new BusinessException("微信身份无效，请重新登录");
        }
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }

    private WechatSessionResponse authorizedResponse(SysUser user, SceneContext scene) {
        String token = authService.issueToken(user);
        WechatSessionResponse response = new WechatSessionResponse();
        String accessStatus = scene.projectId() == null ? "ACTIVE" : permissionService.getProjectAccessStatus(user.getId(), scene.projectId());
        boolean authorized = scene.projectId() == null || permissionService.getInspectionPermissionCodes(user.getId(), scene.projectId()).size() > 0;
        response.setBindingStatus("DISABLED".equals(accessStatus) ? "PROJECT_ACCESS_DISABLED" : authorized ? "BOUND" : "BOUND_NO_PROJECT_ACCESS");
        response.setToken(token);
        response.setUser(authService.getCurrentUserInfo(token));
        response.setProjectId(scene.projectId());
        response.setSourceId(scene.sourceId());
        response.setMessage("DISABLED".equals(accessStatus) ? "当前项目访问已暂停，请联系项目管理员"
                : authorized ? "微信登录成功" : "账号已绑定，但暂无当前项目权限，可提交项目权限申请");
        return response;
    }

    private boolean hasOtherActiveBinding(Long userId, String appId, String openid) {
        Long count = bindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, userId).eq(SysUserWechatBinding::getAppId, appId)
                .eq(SysUserWechatBinding::getStatus, "ACTIVE").ne(SysUserWechatBinding::getOpenid, openid));
        return count != null && count > 0;
    }

    private WechatAccessApplication findPending(String appId, String openid, Long projectId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<WechatAccessApplication>()
                .eq(WechatAccessApplication::getAppId, appId).eq(WechatAccessApplication::getOpenid, openid)
                .eq(WechatAccessApplication::getProjectId, projectId).eq(WechatAccessApplication::getStatus, "PENDING")
                .last("LIMIT 1"));
    }

    private SceneContext resolveScene(String rawScene) {
        if (!StringUtils.hasText(rawScene)) return new SceneContext(null, null);
        String scene = rawScene.trim();
        if (scene.startsWith("B:")) scene = scene.substring(2);
        ElectricBox box = electricBoxMapper.selectOne(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getPublicCode, scene).last("LIMIT 1"));
        return box == null ? new SceneContext(null, null) : new SceneContext(box.getProjectId(), box.getId());
    }

    private record SceneContext(Long projectId, Long sourceId) {}

    public record PendingWechatIdentity(String appId, String openid, String unionid) {}
}
