package com.example.siteplatform.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.CaptchaService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.entity.SysUserProjectRole;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.registration.dto.RegistrationApplicationVO;
import com.example.siteplatform.registration.dto.RegistrationReviewRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitResponse;
import com.example.siteplatform.registration.entity.RegistrationApplication;
import com.example.siteplatform.registration.mapper.RegistrationApplicationMapper;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class RegistrationApplicationService {

    public static final String REGISTRATION_MODE_STANDARD = "STANDARD";
    public static final String REGISTRATION_MODE_WECHAT_QUICK = "WECHAT_QUICK";

    private final RegistrationApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final SystemRoleMapper roleMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final InspectionPermissionTemplateService inspectionTemplateService;
    private final AuthService authService;
    private final CaptchaService captchaService;
    private final WechatAuthService wechatAuthService;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    private SysUserProjectRoleMapper userProjectRoleMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private SysUserWechatBindingMapper wechatBindingMapper;

    public RegistrationApplicationService(RegistrationApplicationMapper applicationMapper, SysUserMapper userMapper,
                                          SystemRoleMapper roleMapper, SysUserProjectMapper userProjectMapper,
                                          InspectionPermissionTemplateService inspectionTemplateService,
                                          AuthService authService, CaptchaService captchaService,
                                          WechatAuthService wechatAuthService, OperationLogMapper operationLogMapper,
                                          ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userProjectMapper = userProjectMapper;
        this.inspectionTemplateService = inspectionTemplateService;
        this.authService = authService;
        this.captchaService = captchaService;
        this.wechatAuthService = wechatAuthService;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegistrationSubmitResponse submit(RegistrationSubmitRequest request) {
        if (request == null) {
            throw new BusinessException("注册申请参数不能为空");
        }
        String source = normalizeSource(StringUtils.hasText(request.getSourceType())
                ? request.getSourceType() : request.getSource());
        String registrationMode = normalizeRegistrationMode(request.getRegistrationMode(), source);
        boolean wechatQuick = REGISTRATION_MODE_WECHAT_QUICK.equals(registrationMode);
        if (wechatQuick && (!StringUtils.hasText(request.getWechatCode()) || !StringUtils.hasText(request.getPhoneCode()))) {
            throw new BusinessException("微信快捷注册需要重新授权微信身份和手机号");
        }
        if (!wechatQuick && !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("请设置登录密码");
        }
        WechatAuthService.PendingWechatIdentity wechatIdentity = null;
        if ("WEB".equals(source)) {
            captchaService.verifyAndConsume(request.getCaptchaId(), request.getCaptchaCode());
        } else {
            wechatIdentity = StringUtils.hasText(request.getWechatCode())
                    ? wechatAuthService.identityForCode(request.getWechatCode())
                    : wechatAuthService.consumePendingIdentity(request.getWechatSessionToken());
        }
        String phone;
        String phoneVerificationType;
        if ("MINI".equals(source) && StringUtils.hasText(request.getPhoneCode())) {
            phone = wechatAuthService.resolvePhone(request.getPhoneCode(), request.getPhone());
            phoneVerificationType = "WECHAT";
        } else {
            phone = trimToNull(request.getPhone());
            phoneVerificationType = "WEB".equals(source) ? "MANUAL_REVIEW"
                    : normalizePhoneVerification(request.getPhoneVerificationType());
        }
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        String submittedUsername = trimToNull(request.getUsername());
        if (submittedUsername != null && !phone.equals(submittedUsername)) {
            throw new BusinessException("注册账号默认使用手机号，请勿单独填写用户名");
        }
        // 新账号的唯一登录账号固定为手机号码。即使旧客户端省略 username，
        // 审批通过后创建的 sys_user 也始终使用同一手机号作为登录名。
        String username = phone;
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            throw conflict("账号已存在，请直接登录");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0) {
            throw conflict("手机号已关联系统账号，请绑定已有账号");
        }
        ensureWechatIdentityAvailable(wechatIdentity);
        String statusToken = randomToken();
        RegistrationApplication application = new RegistrationApplication();
        application.setUsername(username);
        application.setPasswordHash(wechatQuick ? null : authService.hashPassword(request.getPassword()));
        application.setRealName(request.getRealName().trim());
        application.setPhone(phone);
        application.setEmail(trimToNull(request.getEmail()));
        application.setApplicationReason(trimToNull(StringUtils.hasText(request.getApplicationReason())
                ? request.getApplicationReason() : request.getReason()));
        List<Long> desiredProjects = request.getDesiredProjectIds();
        if ((desiredProjects == null || desiredProjects.isEmpty()) && request.getRequestedProjectId() != null) {
            desiredProjects = List.of(request.getRequestedProjectId());
        }
        application.setDesiredProjectIds(writeProjectIds(desiredProjects));
        application.setDesiredProjectText(trimToNull(StringUtils.hasText(request.getDesiredProjectText())
                ? request.getDesiredProjectText() : request.getDesiredProjectName()));
        application.setSourceType(source);
        application.setRegistrationMode(registrationMode);
        application.setPhoneVerificationType(phoneVerificationType);
        if (wechatIdentity != null) {
            application.setAppId(wechatIdentity.appId());
            application.setOpenid(wechatIdentity.openid());
            application.setUnionid(trimToNull(wechatIdentity.unionid()));
        }
        application.setStatus("PENDING");
        application.setStatusTokenHash(digest(statusToken));
        application.setCreateTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
        try {
            if (applicationMapper.insert(application) != 1) {
                throw new BusinessException("注册申请提交失败，请稍后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("该账号或手机号已有待审批申请");
        }
        return new RegistrationSubmitResponse(application.getId(), application.getStatus(), statusToken);
    }

    public RegistrationApplicationVO status(String statusToken) {
        if (!StringUtils.hasText(statusToken)) throw new BusinessException("申请查询令牌不能为空");
        RegistrationApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<RegistrationApplication>()
                        .eq(RegistrationApplication::getStatusTokenHash, digest(statusToken.trim()))
                        .last("LIMIT 1"));
        if (application == null) throw BusinessException.notFound("申请不存在或查询令牌无效");
        return toVO(application);
    }

    @Transactional
    public RegistrationApplicationVO cancel(String statusToken) {
        if (!StringUtils.hasText(statusToken)) throw new BusinessException("申请查询令牌不能为空");
        RegistrationApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<RegistrationApplication>()
                        .eq(RegistrationApplication::getStatusTokenHash, digest(statusToken.trim()))
                        .last("LIMIT 1 FOR UPDATE"));
        if (application == null) throw BusinessException.notFound("申请不存在或查询令牌无效");
        if (!"PENDING".equals(application.getStatus())) throw conflict("该申请已处理，不能取消");
        application.setStatus("CANCELLED");
        application.setPasswordHash(null);
        application.setReviewComment("申请人主动取消");
        application.setReviewTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
        if (finishApplication(application) != 1) {
            throw conflict("注册申请状态已变化，请刷新后重试");
        }
        return toVO(application);
    }

    public PageResult<RegistrationApplicationVO> list(String status, String keyword,
                                                       Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<RegistrationApplication> wrapper = new LambdaQueryWrapper<RegistrationApplication>()
                .orderByAsc(RegistrationApplication::getStatus)
                .orderByDesc(RegistrationApplication::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(RegistrationApplication::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        List<RegistrationApplicationVO> all = applicationMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .filter(item -> !StringUtils.hasText(keyword)
                        || (item.getUsername() + item.getRealName() + item.getPhone())
                        .toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT)))
                .toList();
        int currentPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((currentPage - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(currentPage, size, (long) all.size(), all.subList(from, to));
    }

    public RegistrationApplicationVO detail(Long id) {
        RegistrationApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw BusinessException.notFound("注册申请不存在");
        }
        return toVO(application);
    }

    @Transactional
    public RegistrationApplicationVO approve(Long id, RegistrationReviewRequest request, SysUser reviewer) {
        RegistrationApplication application = requirePending(id);
        if (request == null || !StringUtils.hasText(request.getReviewComment())) {
            throw new BusinessException("审批意见不能为空");
        }
        // 注册审核只创建普通系统账号和项目成员关系；平台全局身份不属于业务授权，
        // 只能由系统初始化或受保护运维流程维护。必须在建账号前校验，避免出现可回滚范围外的副作用。
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            throw new BusinessException("注册审核不授予平台全局身份");
        }
        ensureAccountAvailable(application);
        SysUser user = new SysUser();
        boolean wechatQuick = isWechatQuick(application);
        user.setUsername(application.getUsername());
        user.setPassword(wechatQuick ? authService.createUnusablePasswordHash() : application.getPasswordHash());
        user.setPasswordLoginEnabled(wechatQuick ? 0 : 1);
        user.setCredentialVersion(1);
        user.setPasswordResetRequired(wechatQuick ? 1 : 0);
        user.setRealName(application.getRealName());
        user.setPhone(application.getPhone());
        user.setEmail(application.getEmail());
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw conflict("申请账号已被占用，请刷新后重新审核");
        }

        if (request.getProjectAssignments() != null) {
            for (RegistrationReviewRequest.ProjectAssignment assignment : request.getProjectAssignments()) {
                createProjectAssignment(user.getId(), assignment);
            }
        }
        if (StringUtils.hasText(application.getOpenid())) {
            wechatAuthService.bind(user, application.getAppId(), application.getOpenid(),
                    application.getUnionid(), application.getPhone());
        }
        finishReview(application, "APPROVED", request.getReviewComment(), reviewer);
        application.setCreatedUserId(user.getId());
        application.setPasswordHash(null);
        if (finishApplication(application) != 1) {
            throw conflict("注册申请状态已变化，请刷新后重试");
        }
        record(application, reviewer, "APPROVE_REGISTRATION");
        return toVO(application);
    }

    @Transactional
    public RegistrationApplicationVO reject(Long id, String comment, SysUser reviewer) {
        RegistrationApplication application = requirePending(id);
        if (!StringUtils.hasText(comment)) throw new BusinessException("拒绝原因不能为空");
        finishReview(application, "REJECTED", comment, reviewer);
        application.setPasswordHash(null);
        if (finishApplication(application) != 1) {
            throw conflict("注册申请状态已变化，请刷新后重试");
        }
        record(application, reviewer, "REJECT_REGISTRATION");
        return toVO(application);
    }

    private void createProjectAssignment(Long userId, RegistrationReviewRequest.ProjectAssignment assignment) {
        if (assignment == null || assignment.getProjectId() == null) throw new BusinessException("项目授权不能为空");
        if (assignment.getRoleIds() == null || assignment.getRoleIds().isEmpty()) {
            throw new BusinessException("项目至少需要分配一个项目角色");
        }
        List<SystemRole> roles = new java.util.ArrayList<>();
        for (Long roleId : assignment.getRoleIds().stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            SystemRole role = roleMapper.selectById(roleId);
            if (role == null || !"PROJECT".equalsIgnoreCase(role.getScopeType())
                    || Integer.valueOf(0).equals(role.getEnabled())
                    || Integer.valueOf(1).equals(role.getDeleted())) {
                throw new BusinessException("项目角色不存在：" + roleId);
            }
            roles.add(role);
        }
        if (roles.isEmpty()) throw new BusinessException("项目至少需要分配一个项目角色");
        SysUserProject userProject = new SysUserProject();
        userProject.setUserId(userId);
        userProject.setProjectId(assignment.getProjectId());
        userProject.setProjectRoleCode(roles.stream()
                .sorted(java.util.Comparator.comparing((SystemRole role) -> Integer.valueOf(1).equals(role.getProjectManagerRole())).reversed()
                        .thenComparing(SystemRole::getRoleCode))
                .map(SystemRole::getRoleCode).findFirst().orElse(null));
        userProject.setInspectionPermissionTemplateId(null);
        userProject.setStatus("ACTIVE");
        userProject.setCreateTime(LocalDateTime.now());
        userProject.setUpdateTime(LocalDateTime.now());
        userProjectMapper.insert(userProject);
        for (SystemRole role : roles) {
            SysUserProjectRole relation = new SysUserProjectRole();
            relation.setUserId(userId);
            relation.setProjectId(assignment.getProjectId());
            relation.setRoleId(role.getId());
            relation.setCreateTime(LocalDateTime.now());
            userProjectRoleMapper.insert(relation);
        }
    }

    private RegistrationApplication requirePending(Long id) {
        if (id == null) throw new BusinessException("注册申请ID不能为空");
        RegistrationApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<RegistrationApplication>()
                        .eq(RegistrationApplication::getId, id)
                        .last("LIMIT 1 FOR UPDATE"));
        if (application == null) throw BusinessException.notFound("注册申请不存在");
        if (!"PENDING".equals(application.getStatus())) throw conflict("该申请已处理");
        if (!isWechatQuick(application) && !StringUtils.hasText(application.getPasswordHash())) {
            throw new BusinessException("申请密码已清除，不能批准");
        }
        return application;
    }

    private void ensureAccountAvailable(RegistrationApplication application) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, application.getUsername())) > 0) {
            throw conflict("申请账号已被占用");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, application.getPhone())) > 0) {
            throw conflict("申请手机号已关联系统账号");
        }
    }

    private void ensureWechatIdentityAvailable(WechatAuthService.PendingWechatIdentity identity) {
        if (identity == null) return;
        Long openidCount = wechatBindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, identity.appId())
                .eq(SysUserWechatBinding::getOpenid, identity.openid())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (openidCount != null && openidCount > 0) {
            throw conflict("该微信已绑定系统账号，请直接登录或绑定已有账号");
        }
        if (!StringUtils.hasText(identity.unionid())) return;
        Long unionidCount = wechatBindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getAppId, identity.appId())
                .eq(SysUserWechatBinding::getUnionid, identity.unionid())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (unionidCount != null && unionidCount > 0) {
            throw conflict("该微信已绑定系统账号，请直接登录或绑定已有账号");
        }
    }

    private void finishReview(RegistrationApplication application, String status, String comment, SysUser reviewer) {
        application.setStatus(status);
        application.setReviewerId(reviewer.getId());
        application.setReviewerName(StringUtils.hasText(reviewer.getRealName()) ? reviewer.getRealName() : reviewer.getUsername());
        application.setReviewComment(comment.trim());
        application.setReviewTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
    }

    private int finishApplication(RegistrationApplication application) {
        return applicationMapper.update(null,
                new UpdateWrapper<RegistrationApplication>()
                        .eq("id", application.getId())
                        .eq("status", "PENDING")
                        .set("status", application.getStatus())
                        .set("password_hash", null)
                        .set("created_user_id", application.getCreatedUserId())
                        .set("reviewer_id", application.getReviewerId())
                        .set("reviewer_name", application.getReviewerName())
                        .set("review_comment", application.getReviewComment())
                        .set("review_time", application.getReviewTime())
                        .set("update_time", application.getUpdateTime()));
    }

    private void record(RegistrationApplication application, SysUser reviewer, String operationType) {
        OperationLog log = new OperationLog();
        log.setUserId(reviewer.getId());
        log.setUsername(reviewer.getUsername());
        log.setOperationType(operationType);
        log.setOperationDesc(operationType + "：" + application.getUsername());
        log.setBusinessType("REGISTRATION_APPLICATION");
        log.setBusinessId(application.getId());
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private RegistrationApplicationVO toVO(RegistrationApplication application) {
        RegistrationApplicationVO vo = new RegistrationApplicationVO();
        vo.setId(application.getId());
        vo.setUsername(application.getUsername());
        vo.setRealName(application.getRealName());
        vo.setPhone(application.getPhone());
        vo.setEmail(application.getEmail());
        vo.setApplicationReason(application.getApplicationReason());
        vo.setDesiredProjectIds(readProjectIds(application.getDesiredProjectIds()));
        vo.setDesiredProjectText(application.getDesiredProjectText());
        vo.setSourceType(application.getSourceType());
        vo.setRegistrationMode(application.getRegistrationMode());
        vo.setPhoneVerificationType(application.getPhoneVerificationType());
        vo.setStatus(application.getStatus());
        vo.setCreatedUserId(application.getCreatedUserId());
        vo.setReviewerName(application.getReviewerName());
        vo.setReviewComment(application.getReviewComment());
        vo.setReviewTime(application.getReviewTime());
        vo.setCreateTime(application.getCreateTime());
        return vo;
    }

    private String normalizeSource(String source) {
        String normalized = StringUtils.hasText(source) ? source.trim().toUpperCase(Locale.ROOT) : "WEB";
        if ("MINI_PROGRAM".equals(normalized) || "MINIPROGRAM".equals(normalized)) normalized = "MINI";
        if (!"WEB".equals(normalized) && !"MINI".equals(normalized)) {
            throw new BusinessException("申请来源只支持 WEB 或 MINI");
        }
        return normalized;
    }

    private String normalizeRegistrationMode(String mode, String source) {
        String normalized = StringUtils.hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : REGISTRATION_MODE_STANDARD;
        if (!REGISTRATION_MODE_STANDARD.equals(normalized) && !REGISTRATION_MODE_WECHAT_QUICK.equals(normalized)) {
            throw new BusinessException("注册方式不支持");
        }
        if (REGISTRATION_MODE_WECHAT_QUICK.equals(normalized) && !"MINI".equals(source)) {
            throw new BusinessException("微信快捷注册仅支持微信小程序");
        }
        return normalized;
    }

    private boolean isWechatQuick(RegistrationApplication application) {
        return application != null && REGISTRATION_MODE_WECHAT_QUICK.equalsIgnoreCase(application.getRegistrationMode());
    }

    private String normalizePhoneVerification(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "MANUAL";
        return "WECHAT".equals(value) ? "WECHAT" : "MANUAL";
    }

    private String writeProjectIds(List<Long> projectIds) {
        try {
            return objectMapper.writeValueAsString(projectIds == null ? List.of() : projectIds.stream().distinct().toList());
        } catch (Exception exception) {
            throw new BusinessException("意向项目格式错误");
        }
    }

    private List<Long> readProjectIds(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }
}
