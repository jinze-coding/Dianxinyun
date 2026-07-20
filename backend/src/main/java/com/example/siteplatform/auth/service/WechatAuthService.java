package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.WechatPhoneRequest;
import com.example.siteplatform.auth.dto.WechatSessionRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WechatAuthService {

    private static final String SESSION_PREFIX = "wechat:login-session:";
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
            if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                binding.setLastLoginTime(LocalDateTime.now());
                bindingMapper.updateById(binding);
                return authorizedResponse(user, scene);
            }
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
        String unionid = String.valueOf(session.get("unionid"));
        String phone = platformClient.getPhoneNumber(request.getPhoneCode(), request.getPhone());
        SceneContext scene = resolveScene(request.getScene());
        if (scene.projectId() == null) throw new BusinessException("请从电箱巡检码进入后再申请项目权限");
        SysUserWechatBinding historicalBinding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, appId).eq(SysUserWechatBinding::getOpenid, openid).last("LIMIT 1"));
        if (historicalBinding != null && "DISABLED".equals(historicalBinding.getStatus())) {
            throw BusinessException.forbidden("微信登录已被停用，请联系平台管理员");
        }
        boolean requireApproval = historicalBinding != null && "UNBOUND".equals(historicalBinding.getStatus());
        List<SysUser> matched = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getStatus, 1));
        if (matched.size() == 1 && "DISABLED".equals(permissionService.getProjectAccessStatus(matched.get(0).getId(), scene.projectId()))) {
            WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PROJECT_ACCESS_DISABLED");
            response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
            response.setMessage("当前项目访问已暂停，请联系项目管理员"); return response;
        }
        if (!requireApproval && matched.size() == 1
                && permissionService.getInspectionPermissionCodes(matched.get(0).getId(), scene.projectId()).size() > 0
                && !hasOtherActiveBinding(matched.get(0).getId(), appId, openid)) {
            bind(matched.get(0), appId, openid, unionid, phone);
            redisTemplate.delete(key);
            return authorizedResponse(matched.get(0), scene);
        }
        WechatAccessApplication application = findPending(appId, openid, scene.projectId());
        if (application == null) application = new WechatAccessApplication();
        application.setAppId(appId);
        application.setOpenid(openid);
        application.setPhone(phone);
        application.setRealName(StringUtils.hasText(request.getRealName()) ? request.getRealName().trim() : null);
        application.setProjectId(scene.projectId());
        application.setSourceType("ELECTRIC_BOX");
        application.setSourceId(scene.sourceId());
        application.setMatchedUserId(matched.size() == 1 ? matched.get(0).getId() : null);
        application.setStatus("PENDING");
        application.setUpdateTime(LocalDateTime.now());
        if (application.getId() == null) {
            application.setCreateTime(LocalDateTime.now());
            applicationMapper.insert(application);
        } else applicationMapper.updateById(application);
        WechatSessionResponse response = new WechatSessionResponse();
        response.setBindingStatus("PENDING_APPROVAL");
        response.setApplicationStatus("PENDING");
        response.setProjectId(scene.projectId());
        response.setSourceId(scene.sourceId());
        response.setMessage(matched.size() > 1 ? "手机号匹配到多个账号，已转人工确认" :
                matched.isEmpty() ? "未匹配到平台账号，已提交注册申请" : "账号暂无当前项目权限，已提交权限申请");
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
                .eq(SysUserWechatBinding::getUserId, user.getId()).eq(SysUserWechatBinding::getStatus, "ACTIVE").last("LIMIT 1"));
        if (binding == null) throw new BusinessException("当前账号没有有效微信绑定");
        WechatAccessApplication application = findPending(binding.getAppId(), binding.getOpenid(), scene.projectId());
        if (application == null) application = new WechatAccessApplication();
        application.setAppId(binding.getAppId()); application.setOpenid(binding.getOpenid()); application.setPhone(binding.getPhone());
        application.setRealName(user.getRealName()); application.setProjectId(scene.projectId()); application.setSourceType("ELECTRIC_BOX");
        application.setSourceId(scene.sourceId()); application.setMatchedUserId(user.getId()); application.setStatus("PENDING");
        application.setUpdateTime(LocalDateTime.now());
        if (application.getId() == null) { application.setCreateTime(LocalDateTime.now()); applicationMapper.insert(application); }
        else applicationMapper.updateById(application);
        WechatSessionResponse response = new WechatSessionResponse(); response.setBindingStatus("PENDING_APPROVAL");
        response.setApplicationStatus("PENDING"); response.setProjectId(scene.projectId()); response.setSourceId(scene.sourceId());
        response.setMessage("当前项目权限申请已提交，请等待管理员审批"); return response;
    }

    public void bind(SysUser user, String appId, String openid, String unionid, String phone) {
        if (hasOtherActiveBinding(user.getId(), appId, openid)) {
            throw new BusinessException("该系统账号已绑定其他微信，请先在后台解绑");
        }
        SysUserWechatBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, appId).eq(SysUserWechatBinding::getOpenid, openid).last("LIMIT 1"));
        boolean create = binding == null;
        if (create) binding = new SysUserWechatBinding();
        binding.setUserId(user.getId());
        binding.setAppId(appId);
        binding.setOpenid(openid);
        binding.setUnionid(StringUtils.hasText(unionid) ? unionid : null);
        binding.setPhone(phone);
        binding.setStatus("ACTIVE");
        binding.setDeleted(0);
        binding.setBindTime(LocalDateTime.now());
        binding.setLastLoginTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        if (create) { binding.setCreateTime(LocalDateTime.now()); bindingMapper.insert(binding); }
        else bindingMapper.updateById(binding);
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
}
