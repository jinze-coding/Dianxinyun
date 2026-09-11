package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.siteaccess.entity.SiteGuardMeetingRegistration;
import com.example.siteplatform.siteaccess.mapper.SiteGuardMeetingRegistrationMapper;
import com.example.siteplatform.siteaccess.vo.PublicGuardMeetingChoiceVO;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitorSessionRequest;
import com.example.siteplatform.siteaccess.dto.SiteGuardVisitRegistrationUpdateRequest;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitPerson;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitQr;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitRegistration;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.vo.PublicGuardVisitPassVO;
import com.example.siteplatform.siteaccess.vo.PublicGuardVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitAuditVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitPersonVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitQrVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitRegistrationVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GuardVisitService {
    public static final String QR_ENABLED = "ENABLED";
    public static final String QR_DISABLED = "DISABLED";
    public static final String QR_ROTATED = "ROTATED";
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_VOIDED = "VOIDED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String PAGE_FORM = "FORM";
    public static final String PAGE_REGISTERED = "REGISTERED";
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SiteGuardVisitQrMapper qrMapper;
    private final SiteGuardVisitRegistrationMapper registrationMapper;
    private final SiteGuardVisitPersonMapper personMapper;
    private final SiteGuardVisitAuditLogMapper auditMapper;
    private final ProjectInfoMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final ProjectProfileService projectProfileService;
    private final VisitorDataCryptoService cryptoService;
    private final VisitorSessionService sessionService;
    private final VisitorProfileService profileService;
    private final VisitorPersonalProfileService personalProfiles;
    private final GuardVisitorMatchingService matching;
    private final GuardMeetingChoiceService meetingChoices;
    private final MeetingVisitService meetingVisits;
    private final SiteGuardMeetingRegistrationMapper meetingLinks;

    private final WechatPlatformClient wechatPlatformClient;
    private final RedisRateLimitService rateLimitService;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String miniProgramPage;
    private final String miniProgramEnvVersion;

    public GuardVisitService(SiteGuardVisitQrMapper qrMapper,
                             SiteGuardVisitRegistrationMapper registrationMapper,
                             SiteGuardVisitPersonMapper personMapper,
                             SiteGuardVisitAuditLogMapper auditMapper,
                             ProjectInfoMapper projectMapper,
                             ProjectPermissionService permissionService,
                             ProjectProfileService projectProfileService,
                             VisitorDataCryptoService cryptoService,
                             VisitorSessionService sessionService,
                             VisitorProfileService profileService,
                             VisitorPersonalProfileService personalProfiles, GuardVisitorMatchingService matching,
                             GuardMeetingChoiceService meetingChoices, MeetingVisitService meetingVisits,
                             SiteGuardMeetingRegistrationMapper meetingLinks,
                             WechatPlatformClient wechatPlatformClient,
                             RedisRateLimitService rateLimitService,
                             OperationLogMapper operationLogMapper,
                             ObjectMapper objectMapper,
                             @Value("${wechat.mini-program.guard-visitor-page:pages/public/guard-visitor-register}") String miniProgramPage,
                             @Value("${wechat.mini-program.env-version:release}") String miniProgramEnvVersion) {
        this.qrMapper = qrMapper;
        this.registrationMapper = registrationMapper;
        this.personMapper = personMapper;
        this.auditMapper = auditMapper;
        this.projectMapper = projectMapper;
        this.permissionService = permissionService;
        this.projectProfileService = projectProfileService;
        this.cryptoService = cryptoService;
        this.sessionService = sessionService;
        this.profileService = profileService;
        this.personalProfiles = personalProfiles;
        this.matching = matching;
        this.meetingChoices = meetingChoices;
        this.meetingVisits = meetingVisits;
        this.meetingLinks = meetingLinks;
        this.wechatPlatformClient = wechatPlatformClient;
        this.rateLimitService = rateLimitService;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.miniProgramPage = miniProgramPage;
        this.miniProgramEnvVersion = miniProgramEnvVersion;
    }

    public SiteGuardVisitQrVO currentQr(Long projectId, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_VIEW);
        ProjectInfo project = requireProject(projectId);
        SiteGuardVisitQr qr = findCurrentQr(projectId);
        return qr == null ? null : toQrVO(qr, project);
    }

    @Transactional
    public SiteGuardVisitQrVO createQr(Long projectId, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ProjectInfo project = requireProjectForUpdate(projectId);
        SiteGuardVisitQr current = qrMapper.selectCurrentForUpdate(projectId);
        if (current != null) return toQrVO(current, project);
        SiteGuardVisitQr created = newQr(projectId, 1, QR_ENABLED, user);
        try {
            requireSingle(qrMapper.insert(created), "门卫登记码创建");
        } catch (DuplicateKeyException exception) {
            throw stateConflict("门卫登记码已由其他操作创建，请刷新后重试");
        }
        recordOperation(user, "CREATE_GUARD_VISIT_QR", created.getId(), "创建项目门卫访客登记码");
        return toQrVO(created, project);
    }

    @Transactional
    public SiteGuardVisitQrVO changeStatus(Long id, Boolean enabled, Integer expectedVersion, SysUser user) {
        SiteGuardVisitQr qr = requireQr(id, true);
        requirePermission(user, qr.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        requireCurrentQr(qr);
        requireVersion(qr, expectedVersion);
        String target = Boolean.TRUE.equals(enabled) ? QR_ENABLED : QR_DISABLED;
        if (target.equals(qr.getQrStatus())) return toQrVO(qr, requireProject(qr.getProjectId()));
        qr.setQrStatus(target);
        touchQr(qr, user);
        requireSingle(qrMapper.updateById(qr), "门卫登记码启停");
        recordOperation(user, Boolean.TRUE.equals(enabled) ? "ENABLE_GUARD_VISIT_QR" : "DISABLE_GUARD_VISIT_QR",
                qr.getId(), Boolean.TRUE.equals(enabled) ? "启用项目门卫访客登记码" : "停用项目门卫访客登记码");
        return toQrVO(qr, requireProject(qr.getProjectId()));
    }

    @Transactional
    public SiteGuardVisitQrVO rotate(Long id, Integer expectedVersion, SysUser user) {
        SiteGuardVisitQr currentBeforeLock = requireQr(id, false);
        requirePermission(user, currentBeforeLock.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ProjectInfo project = requireProjectForUpdate(currentBeforeLock.getProjectId());
        SiteGuardVisitQr current = qrMapper.selectCurrentForUpdate(currentBeforeLock.getProjectId());
        if (current == null || !Objects.equals(current.getId(), id)) throw stateConflict("门卫登记码已轮换，请刷新后重试");
        requireVersion(current, expectedVersion);
        String nextStatus = QR_DISABLED.equals(current.getQrStatus()) ? QR_DISABLED : QR_ENABLED;
        current.setQrStatus(QR_ROTATED);
        touchQr(current, user);
        requireSingle(qrMapper.updateById(current), "门卫登记码轮换");

        SiteGuardVisitQr replacement = newQr(current.getProjectId(), current.getQrVersion() + 1, nextStatus, user);
        try {
            requireSingle(qrMapper.insert(replacement), "新门卫登记码创建");
        } catch (DuplicateKeyException exception) {
            throw stateConflict("门卫登记码轮换冲突，请刷新后重试");
        }
        recordOperation(user, "ROTATE_GUARD_VISIT_QR", replacement.getId(),
                "轮换项目门卫访客登记码至第" + replacement.getQrVersion() + "版");
        return toQrVO(replacement, project);
    }

    public SiteGuardVisitMiniCodeVO miniCode(Long id, SysUser user) {
        SiteGuardVisitQr qr = requireQr(id, false);
        requirePermission(user, qr.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        requireCurrentQr(qr);
        ProjectInfo project = requireProject(qr.getProjectId());
        String scene = "G:" + cryptoService.decrypt(qr.getSceneTokenEncrypted());
        String image = wechatPlatformClient.generateUnlimitedCode(scene, miniProgramPage, miniProgramEnvVersion);
        SiteGuardVisitMiniCodeVO vo = new SiteGuardVisitMiniCodeVO();
        vo.setGuardQrId(qr.getId());
        vo.setProjectId(qr.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setQrStatus(qr.getQrStatus());
        vo.setQrVersion(qr.getQrVersion());
        vo.setPagePath(miniProgramPage);
        vo.setCodeType(image == null ? "UNAVAILABLE" : "WECHAT_MINI_PROGRAM_CODE");
        vo.setImageMimeType(image == null ? null : "image/png");
        vo.setImageContent(image);
        vo.setHint(image == null
                ? "当前环境未配置可用的微信小程序码凭据，未返回门卫登记令牌"
                : "门卫室固定登记码长期有效；每次访客登记的放行状态有效24小时");
        return vo;
    }

    public PublicGuardVisitorSessionVO createPublicSession(PublicGuardVisitorSessionRequest request) {
        if (request == null) throw new BusinessException("门卫登记会话参数不能为空");
        SiteGuardVisitQr qr = resolveQr(request.getSceneToken());
        ProjectInfo project = requireUsableProject(qr.getProjectId());
        PublicVisitorSessionVO issued = sessionService.issueGuard(
                request.getWechatCode(), qr.getId(), qr.getProjectId());
        VisitorSessionService.VisitorSessionContext context = requireGuardContext(issued.getVisitorSessionToken());
        PublicGuardVisitorSessionVO vo = publicState(context, project);
        vo.setVisitorSessionToken(issued.getVisitorSessionToken());
        vo.setExpiresInSeconds(issued.getExpiresInSeconds());
        return vo;
    }

    @Transactional(readOnly = true)
    public PublicGuardVisitorSessionVO refreshPublicState(String visitorSessionToken) {
        var context = requireGuardContext(visitorSessionToken);
        rateLimitService.check("public-site-guard-state-identity", guardIdentityHash(context), 60, Duration.ofMinutes(10));
        return publicState(context, requireUsableProject(context.projectId()));
    }

    public List<PublicGuardMeetingChoiceVO> publicMeetings(String visitorSessionToken) {
        var context = requireGuardContext(visitorSessionToken);
        rateLimitService.check("public-site-guard-meetings-identity", guardIdentityHash(context), 60, Duration.ofMinutes(10));
        return meetingChoices.list(context, visitorSessionToken);
    }

    private PublicGuardVisitorSessionVO publicState(VisitorSessionService.VisitorSessionContext context, ProjectInfo project) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        var active = findActive(context, now);
        var matched = matching.find(context, now);
        var vo = new PublicGuardVisitorSessionVO();
        vo.setPageState(!matched.isEmpty() ? "MATCHED" : active != null ? PAGE_REGISTERED : PAGE_FORM);
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setServerTime(now);
        vo.setMatchedPasses(matched);
        if (active != null) vo.setRegistration(toPass(active, project, now));
        if (PAGE_FORM.equals(vo.getPageState())) vo.setPersonalInfo(personalProfiles.read(context));
        return vo;
    }

    public PublicProjectProfileVO publicProjectProfile(String sceneToken) {
        SiteGuardVisitQr qr = resolveQr(sceneToken);
        requireUsableProject(qr.getProjectId());
        return projectProfileService.getPublicProfile(qr.getProjectId());
    }

    public ProjectProfileService.PublicProjectProfileImageContent publicProjectProfileImage(
            String sceneToken, Integer imageIndex) {
        SiteGuardVisitQr qr = resolveQr(sceneToken);
        requireUsableProject(qr.getProjectId());
        return projectProfileService.getPublicProfileImage(qr.getProjectId(), imageIndex);
    }

    @Transactional
    public PublicGuardVisitPassVO submitPublic(PublicGuardVisitSubmitRequest request, String visitorSessionToken) {
        if (request == null) throw new BusinessException("门卫访客登记参数不能为空");
        VisitorSessionService.VisitorSessionContext initial = sessionService.require(visitorSessionToken);
        if (!VisitorSessionService.SOURCE_GUARD_QR.equals(initial.effectiveSourceType())) {
            throw BusinessException.of(403, "当前访客会话不能用于门卫登记");
        }
        rateLimitService.check("public-site-guard-submit-identity", guardIdentityHash(initial),
                10, Duration.ofMinutes(30));
        ProjectInfo project = requireProjectForUpdate(initial.projectId());
        ensureProjectActive(project);
        SiteGuardVisitQr qr = qrMapper.selectForUpdate(initial.effectiveSourceId());
        validateUsableQr(qr);
        VisitorSessionService.VisitorSessionContext context = sessionService.requireGuard(
                visitorSessionToken, qr.getId(), qr.getProjectId());
        LocalDateTime now = LocalDateTime.now();
        SiteGuardVisitRegistration existing = registrationMapper.selectActiveForUpdate(
                context.projectId(), context.appId(), guardIdentityHash(context), now);
        var choices = meetingChoices.resolve(request.getMeetingChoiceTokens(), context, visitorSessionToken);
        if (existing != null && choices.isEmpty()) return toPass(existing, project, now);

        VisitorSubmissionNormalizer.Submission submission = VisitorSubmissionNormalizer.normalize(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getCompanions(), request.getTravelMode(), request.getVehiclePlate(),
                request.getVisitorRemark());
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) throw new BusinessException("请阅读并同意隐私告知");
        Long sourceProfileId = profileService.applyOnSubmission(
                context, request.getProfileAction(), request.getProfileCode(), request.getProfileName(),
                request.getProfileRetentionAgreed(), request.getProfileVersion(), toProfileSubmission(submission));
        if (existing != null) {
            if (registerMeetings(existing, context, choices, submission) > 0) {
                personalProfiles.saveOnSubmission(context, request.getRememberInfo(), submission);
            }
            return toPass(existing, project, now);
        }

        SiteGuardVisitRegistration registration = new SiteGuardVisitRegistration();
        registration.setRegistrationNo(generateRegistrationNo(now));
        registration.setProjectId(qr.getProjectId());
        registration.setGuardQrId(qr.getId());
        registration.setWechatAppId(context.appId());
        registration.setVisitorIdentityHash(guardIdentityHash(context));
        registration.setStatus(STATUS_REGISTERED);
        applySubmission(registration, submission);
        registration.setSourceProfileId(sourceProfileId);
        registration.setPrivacyAgreedTime(now);
        registration.setRegisteredTime(now);
        registration.setValidUntil(now.plusHours(24));
        registration.setVersion(0);
        registration.setDeleted(0);
        registration.setCreateTime(now);
        registration.setUpdateTime(now);
        try {
            requireSingle(registrationMapper.insert(registration), "门卫访客登记");
        } catch (DuplicateKeyException exception) {
            throw stateConflict("门卫访客登记冲突，请重试");
        }
        replacePeople(registration, submission);
        registerMeetings(registration, context, choices, submission);
        personalProfiles.saveOnSubmission(context, request.getRememberInfo(), submission);
        writeAudit(registration, "REGISTER", null, null, snapshot(registration), "访客扫码完成免审批登记");
        return toPass(registration, project, now);
    }

    private int registerMeetings(SiteGuardVisitRegistration guard,
            VisitorSessionService.VisitorSessionContext context, List<GuardMeetingChoiceService.Choice> choices,
            VisitorSubmissionNormalizer.Submission submission) {
        int added = 0;
        for (var choice : choices) {
            var registration = meetingVisits.registerFromGuard(context, choice, submission, guard.getSourceProfileId());
            long count = meetingLinks.selectCount(new LambdaQueryWrapper<SiteGuardMeetingRegistration>()
                    .eq(SiteGuardMeetingRegistration::getGuardRegistrationId, guard.getId())
                    .eq(SiteGuardMeetingRegistration::getMeetingRegistrationId, registration.getId()));
            if (count != 0) continue;
            var link = new SiteGuardMeetingRegistration();
            link.setProjectId(context.projectId());
            link.setGuardRegistrationId(guard.getId());
            link.setInvitationId(registration.getInvitationId());
            link.setMeetingRegistrationId(registration.getId());
            link.setCreateTime(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            requireSingle(meetingLinks.insert(link), "门卫会议预约关联");
            added++;
        }
        if (added > 0) writeAudit(guard, "MEETING_RESERVATION", null, null, snapshot(guard),
                "门卫登记关联" + added + "场会议预约；会场签到单独进行");
        return added;
    }

    public List<SiteVisitorProfileVO> publicProfiles(String visitorSessionToken) {
        return profileService.publicList(requireGuardContext(visitorSessionToken));
    }

    public SiteVisitorProfileVO publicProfile(String visitorSessionToken, String profileCode) {
        return profileService.publicDetail(requireGuardContext(visitorSessionToken), profileCode);
    }

    public void disablePublicProfile(String visitorSessionToken, String profileCode) {
        profileService.publicDisable(requireGuardContext(visitorSessionToken), profileCode);
    }

    public PageResult<SiteGuardVisitRegistrationVO> page(Long projectId, String status, String keyword,
                                                         LocalDate startDate, LocalDate endDate,
                                                         Integer pageNo, Integer pageSize, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_VIEW);
        DateRange range = normalizeOptionalRange(startDate, endDate);
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        Page<SiteGuardVisitRegistration> result = registrationMapper.selectPage(
                new Page<>(current, size), query(projectId, status, keyword, range));
        ProjectInfo project = requireProject(projectId);
        return PageResult.of(current, size, result.getTotal(), result.getRecords().stream()
                .map(item -> toVO(item, project, false)).toList());
    }

    public SiteGuardVisitRegistrationVO detail(Long id, SysUser user) {
        SiteGuardVisitRegistration registration = requireRegistration(id);
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        return toVO(registration, requireProject(registration.getProjectId()), true);
    }

    @Transactional
    public SiteGuardVisitRegistrationVO update(Long id, SiteGuardVisitRegistrationUpdateRequest request, SysUser user) {
        if (request == null) throw new BusinessException("纠错参数不能为空");
        SiteGuardVisitRegistration registration = registrationMapper.selectForUpdate(id);
        if (registration == null) throw BusinessException.notFound("门卫访客登记不存在");
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        if (!STATUS_REGISTERED.equals(effectiveStatus(registration))) {
            throw stateConflict("已过期或已作废登记不能纠错");
        }
        requireVersion(registration, request.getVersion());
        VisitorSubmissionNormalizer.Submission submission = VisitorSubmissionNormalizer.normalize(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getCompanions(), request.getTravelMode(), request.getVehiclePlate(),
                request.getVisitorRemark());
        Map<String, Object> before = snapshot(registration);
        applySubmission(registration, submission);
        replacePeople(registration, submission);
        registration.setVersion(versionOf(registration) + 1);
        registration.setUpdateTime(LocalDateTime.now());
        requireSingle(registrationMapper.updateById(registration), "门卫访客登记纠错");
        writeAudit(registration, "UPDATE", user, before, snapshot(registration), "后台纠错门卫访客登记");
        recordOperation(user, "UPDATE_GUARD_VISIT", registration.getId(),
                "纠错门卫访客登记 " + registration.getRegistrationNo());
        return toVO(registration, requireProject(registration.getProjectId()), true);
    }

    @Transactional
    public SiteGuardVisitRegistrationVO voidRegistration(Long id, String reason, SysUser user) {
        SiteGuardVisitRegistration registration = registrationMapper.selectForUpdate(id);
        if (registration == null) throw BusinessException.notFound("门卫访客登记不存在");
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        if (STATUS_VOIDED.equals(registration.getStatus())) throw stateConflict("门卫访客登记已经作废");
        String normalizedReason = VisitorSubmissionNormalizer.requiredText(reason, 300, "作废原因");
        Map<String, Object> before = snapshot(registration);
        registration.setStatus(STATUS_VOIDED);
        registration.setVoidReason(normalizedReason);
        registration.setVoidedById(user.getId());
        registration.setVoidedByName(displayName(user));
        registration.setVoidedTime(LocalDateTime.now());
        registration.setVersion(versionOf(registration) + 1);
        registration.setUpdateTime(LocalDateTime.now());
        requireSingle(registrationMapper.updateById(registration), "门卫访客登记作废");
        writeAudit(registration, "VOID", user, before, snapshot(registration), normalizedReason);
        recordOperation(user, "VOID_GUARD_VISIT", registration.getId(),
                "作废门卫访客登记 " + registration.getRegistrationNo());
        return toVO(registration, requireProject(registration.getProjectId()), true);
    }

    public ExportFile export(Long projectId, String status, String keyword, LocalDate startDate,
                             LocalDate endDate, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_EXPORT);
        DateRange range = requireExportRange(startDate, endDate);
        List<SiteGuardVisitRegistration> registrations = registrationMapper.selectList(
                query(projectId, status, keyword, range));
        List<Long> ids = registrations.stream().map(SiteGuardVisitRegistration::getId).toList();
        Map<Long, List<SiteGuardVisitPerson>> people = ids.isEmpty() ? Map.of()
                : personMapper.selectList(new LambdaQueryWrapper<SiteGuardVisitPerson>()
                .in(SiteGuardVisitPerson::getRegistrationId, ids)
                .orderByAsc(SiteGuardVisitPerson::getRegistrationId)
                .orderByAsc(SiteGuardVisitPerson::getSortOrder))
                .stream().collect(Collectors.groupingBy(SiteGuardVisitPerson::getRegistrationId,
                        LinkedHashMap::new, Collectors.toList()));
        int rows = people.values().stream().mapToInt(List::size).sum();
        if (rows > MAX_EXPORT_ROWS) throw new BusinessException("导出人员超过50000人，请缩小日期范围");
        ProjectInfo project = requireProject(projectId);
        byte[] content = buildWorkbook(project, registrations, people);
        writeProjectAudit(projectId, "EXPORT", user,
                Map.of("startDate", range.start().toString(), "endDate", range.end().toString(), "visitorRows", rows),
                "导出门卫访客登记 " + range.start() + " 至 " + range.end() + "，共" + rows + "人");
        recordOperation(user, "EXPORT_GUARD_VISIT", projectId,
                "导出门卫访客登记，共" + rows + "人");
        String projectName = safeFileName(StringUtils.hasText(project.getShortName())
                ? project.getShortName() : project.getProjectName());
        return new ExportFile("场内管理_门卫访客登记_" + projectName + "_"
                + FILE_DATE.format(range.start()) + "-" + FILE_DATE.format(range.end()) + ".xlsx", content);
    }

    private VisitorSessionService.VisitorSessionContext requireGuardContext(String token) {
        VisitorSessionService.VisitorSessionContext context = sessionService.require(token);
        if (!VisitorSessionService.SOURCE_GUARD_QR.equals(context.effectiveSourceType())) {
            throw BusinessException.of(403, "当前访客会话不能用于门卫登记");
        }
        SiteGuardVisitQr qr = requireQr(context.effectiveSourceId(), false);
        validateUsableQr(qr);
        requireUsableProject(qr.getProjectId());
        return sessionService.requireGuard(token, qr.getId(), qr.getProjectId());
    }

    private SiteGuardVisitQr resolveQr(String rawScene) {
        String token = normalizeSceneToken(rawScene);
        SiteGuardVisitQr qr = qrMapper.selectOne(new LambdaQueryWrapper<SiteGuardVisitQr>()
                .eq(SiteGuardVisitQr::getSceneTokenHash, cryptoService.digest(token)).last("LIMIT 1"));
        if (qr == null) throw BusinessException.notFound("门卫登记入口不存在");
        validateUsableQr(qr);
        return qr;
    }

    private void validateUsableQr(SiteGuardVisitQr qr) {
        if (qr == null) throw BusinessException.notFound("门卫登记入口不存在");
        if (QR_ROTATED.equals(qr.getQrStatus())) throw BusinessException.of(410, "门卫登记码已轮换");
        if (QR_DISABLED.equals(qr.getQrStatus())) throw BusinessException.of(410, "门卫登记入口已停用");
        if (!QR_ENABLED.equals(qr.getQrStatus())) throw BusinessException.notFound("门卫登记入口不存在");
    }

    private SiteGuardVisitRegistration findActive(VisitorSessionService.VisitorSessionContext context,
                                                  LocalDateTime now) {
        return registrationMapper.selectOne(new LambdaQueryWrapper<SiteGuardVisitRegistration>()
                .eq(SiteGuardVisitRegistration::getProjectId, context.projectId())
                .eq(SiteGuardVisitRegistration::getWechatAppId, context.appId())
                .eq(SiteGuardVisitRegistration::getVisitorIdentityHash, guardIdentityHash(context))
                .eq(SiteGuardVisitRegistration::getStatus, STATUS_REGISTERED)
                .gt(SiteGuardVisitRegistration::getValidUntil, now)
                .orderByDesc(SiteGuardVisitRegistration::getRegisteredTime)
                .orderByDesc(SiteGuardVisitRegistration::getId)
                .last("LIMIT 1"));
    }

    private LambdaQueryWrapper<SiteGuardVisitRegistration> query(Long projectId, String status,
                                                                  String keyword, DateRange range) {
        LambdaQueryWrapper<SiteGuardVisitRegistration> query = new LambdaQueryWrapper<SiteGuardVisitRegistration>()
                .eq(SiteGuardVisitRegistration::getProjectId, projectId)
                .orderByDesc(SiteGuardVisitRegistration::getRegisteredTime)
                .orderByDesc(SiteGuardVisitRegistration::getId);
        if (StringUtils.hasText(status)) {
            String normalized = normalizeStatus(status);
            if (STATUS_EXPIRED.equals(normalized)) {
                query.eq(SiteGuardVisitRegistration::getStatus, STATUS_REGISTERED)
                        .le(SiteGuardVisitRegistration::getValidUntil, LocalDateTime.now());
            } else if (STATUS_REGISTERED.equals(normalized)) {
                query.eq(SiteGuardVisitRegistration::getStatus, STATUS_REGISTERED)
                        .gt(SiteGuardVisitRegistration::getValidUntil, LocalDateTime.now());
            } else {
                query.eq(SiteGuardVisitRegistration::getStatus, normalized);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("查询关键词不能超过100个字符");
            query.and(item -> item.like(SiteGuardVisitRegistration::getRegistrationNo, value)
                    .or().like(SiteGuardVisitRegistration::getVisitorCompany, value)
                    .or().like(SiteGuardVisitRegistration::getContactName, value)
                    .or().like(SiteGuardVisitRegistration::getVehiclePlate, value));
        }
        if (range != null) {
            query.ge(SiteGuardVisitRegistration::getRegisteredTime, range.start().atStartOfDay())
                    .lt(SiteGuardVisitRegistration::getRegisteredTime, range.end().plusDays(1).atStartOfDay());
        }
        return query;
    }

    private void applySubmission(SiteGuardVisitRegistration registration,
                                 VisitorSubmissionNormalizer.Submission submission) {
        registration.setVisitorCompany(submission.visitorCompany());
        registration.setContactName(submission.contactName());
        registration.setContactPhoneEncrypted(cryptoService.encrypt(submission.contactPhone()));
        registration.setVisitorCount(submission.people().size());
        registration.setTravelMode(submission.travelMode());
        registration.setVehiclePlate(submission.vehiclePlate());
        registration.setVisitorRemark(submission.visitorRemark());
    }

    private void replacePeople(SiteGuardVisitRegistration registration,
                               VisitorSubmissionNormalizer.Submission submission) {
        personMapper.delete(new LambdaQueryWrapper<SiteGuardVisitPerson>()
                .eq(SiteGuardVisitPerson::getRegistrationId, registration.getId()));
        int order = 1;
        for (VisitorSubmissionNormalizer.Person value : submission.people()) {
            SiteGuardVisitPerson person = new SiteGuardVisitPerson();
            person.setRegistrationId(registration.getId());
            person.setProjectId(registration.getProjectId());
            person.setPersonType(value.personType());
            person.setPersonCompany(value.personCompany());
            person.setPersonName(value.personName());
            person.setPhoneEncrypted(cryptoService.encrypt(value.personPhone()));
            person.setSortOrder(order++);
            person.setDeleted(0);
            person.setCreateTime(LocalDateTime.now());
            person.setUpdateTime(LocalDateTime.now());
            requireSingle(personMapper.insert(person), "门卫访客人员写入");
        }
    }

    private VisitorProfileService.SubmissionData toProfileSubmission(
            VisitorSubmissionNormalizer.Submission submission) {
        return new VisitorProfileService.SubmissionData(
                submission.visitorCompany(), submission.contactName(), submission.contactPhone(),
                submission.travelMode(), submission.vehiclePlate(), submission.people().stream()
                .map(person -> new VisitorProfileService.PersonData(
                        person.personType(), person.personCompany(), person.personName(), person.personPhone()))
                .toList());
    }

    private PublicGuardVisitPassVO toPass(SiteGuardVisitRegistration registration, ProjectInfo project,
                                          LocalDateTime serverTime) {
        PublicGuardVisitPassVO vo = new PublicGuardVisitPassVO();
        vo.setRegistrationNo(registration.getRegistrationNo());
        vo.setStatus(effectiveStatus(registration));
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setVisitorCompany(registration.getVisitorCompany());
        vo.setContactName(registration.getContactName());
        vo.setVisitorCount(registration.getVisitorCount());
        vo.setTravelMode(registration.getTravelMode());
        vo.setVehiclePlate(registration.getVehiclePlate());
        vo.setRegisteredTime(registration.getRegisteredTime());
        vo.setValidUntil(registration.getValidUntil());
        vo.setServerTime(serverTime);
        return vo;
    }

    private SiteGuardVisitRegistrationVO toVO(SiteGuardVisitRegistration registration,
                                               ProjectInfo project, boolean detail) {
        SiteGuardVisitRegistrationVO vo = new SiteGuardVisitRegistrationVO();
        vo.setId(registration.getId());
        vo.setRegistrationNo(registration.getRegistrationNo());
        vo.setProjectId(registration.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setStatus(effectiveStatus(registration));
        vo.setVisitorCompany(registration.getVisitorCompany());
        vo.setContactName(registration.getContactName());
        vo.setVisitorCount(registration.getVisitorCount());
        vo.setTravelMode(registration.getTravelMode());
        vo.setVehiclePlate(registration.getVehiclePlate());
        vo.setRegisteredTime(registration.getRegisteredTime());
        vo.setValidUntil(registration.getValidUntil());
        vo.setVoidReason(registration.getVoidReason());
        vo.setVoidedByName(registration.getVoidedByName());
        vo.setVoidedTime(registration.getVoidedTime());
        vo.setVersion(registration.getVersion());
        vo.setCreateTime(registration.getCreateTime());
        vo.setUpdateTime(registration.getUpdateTime());
        vo.setSourceProfileName(profileService.sourceName(registration.getSourceProfileId()));
        if (detail) {
            vo.setContactPhone(cryptoService.decrypt(registration.getContactPhoneEncrypted()));
            vo.setVisitorRemark(registration.getVisitorRemark());
            vo.setVisitors(people(registration.getId()).stream().map(this::toPersonVO).toList());
            vo.setAuditLogs(auditMapper.selectList(new LambdaQueryWrapper<SiteGuardVisitAuditLog>()
                    .eq(SiteGuardVisitAuditLog::getRegistrationId, registration.getId())
                    .orderByAsc(SiteGuardVisitAuditLog::getCreateTime)
                    .orderByAsc(SiteGuardVisitAuditLog::getId)).stream().map(this::toAuditVO).toList());
        }
        return vo;
    }

    private SiteGuardVisitPersonVO toPersonVO(SiteGuardVisitPerson person) {
        SiteGuardVisitPersonVO vo = new SiteGuardVisitPersonVO();
        vo.setPersonType(person.getPersonType());
        vo.setPersonCompany(person.getPersonCompany());
        vo.setPersonName(person.getPersonName());
        vo.setPersonPhone(cryptoService.decrypt(person.getPhoneEncrypted()));
        vo.setSortOrder(person.getSortOrder());
        return vo;
    }

    private SiteGuardVisitAuditVO toAuditVO(SiteGuardVisitAuditLog audit) {
        SiteGuardVisitAuditVO vo = new SiteGuardVisitAuditVO();
        vo.setActionType(audit.getActionType());
        vo.setOperatorName(audit.getOperatorName());
        vo.setComment(audit.getComment());
        vo.setCreateTime(audit.getCreateTime());
        return vo;
    }

    private List<SiteGuardVisitPerson> people(Long registrationId) {
        return personMapper.selectList(new LambdaQueryWrapper<SiteGuardVisitPerson>()
                .eq(SiteGuardVisitPerson::getRegistrationId, registrationId)
                .orderByAsc(SiteGuardVisitPerson::getSortOrder)
                .orderByAsc(SiteGuardVisitPerson::getId));
    }

    private Map<String, Object> snapshot(SiteGuardVisitRegistration registration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registrationNo", registration.getRegistrationNo());
        result.put("status", effectiveStatus(registration));
        result.put("visitorCompany", registration.getVisitorCompany());
        result.put("contactName", registration.getContactName());
        result.put("contactPhone", cryptoService.decrypt(registration.getContactPhoneEncrypted()));
        result.put("visitorCount", registration.getVisitorCount());
        result.put("travelMode", registration.getTravelMode());
        result.put("vehiclePlate", registration.getVehiclePlate());
        result.put("visitorRemark", registration.getVisitorRemark());
        result.put("registeredTime", registration.getRegisteredTime());
        result.put("validUntil", registration.getValidUntil());
        result.put("voidReason", registration.getVoidReason());
        result.put("visitors", people(registration.getId()).stream().map(person -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("personType", person.getPersonType());
            item.put("personCompany", person.getPersonCompany());
            item.put("personName", person.getPersonName());
            item.put("personPhone", cryptoService.decrypt(person.getPhoneEncrypted()));
            item.put("sortOrder", person.getSortOrder());
            return item;
        }).toList());
        return result;
    }

    private void writeAudit(SiteGuardVisitRegistration registration, String action, SysUser operator,
                            Map<String, Object> before, Map<String, Object> after, String comment) {
        SiteGuardVisitAuditLog audit = new SiteGuardVisitAuditLog();
        audit.setRegistrationId(registration.getId());
        audit.setProjectId(registration.getProjectId());
        audit.setActionType(action);
        audit.setOperatorId(operator == null ? null : operator.getId());
        audit.setOperatorName(operator == null ? "外访人员" : displayName(operator));
        audit.setBeforeSnapshotEncrypted(encryptSnapshot(before));
        audit.setAfterSnapshotEncrypted(encryptSnapshot(after));
        audit.setComment(VisitorSubmissionNormalizer.optionalText(comment, 500, "审计说明"));
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "门卫访客审计日志写入");
    }

    private void writeProjectAudit(Long projectId, String action, SysUser user,
                                   Map<String, Object> after, String comment) {
        SiteGuardVisitAuditLog audit = new SiteGuardVisitAuditLog();
        audit.setProjectId(projectId);
        audit.setActionType(action);
        audit.setOperatorId(user.getId());
        audit.setOperatorName(displayName(user));
        audit.setAfterSnapshotEncrypted(encryptSnapshot(after));
        audit.setComment(comment);
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "门卫访客导出审计日志写入");
    }

    private String encryptSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("门卫访客审计快照生成失败", exception);
        }
    }

    private byte[] buildWorkbook(ProjectInfo project, List<SiteGuardVisitRegistration> registrations,
                                 Map<Long, List<SiteGuardVisitPerson>> peopleByRegistration) {
        String[] headers = {"项目", "登记编号", "单位", "人员类型", "姓名", "手机号码", "出行方式", "车牌号",
                "登记时间", "有效截止时间", "状态"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("门卫访客登记");
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
                header.getCell(index).setCellStyle(headerStyle);
            }
            int rowNumber = 1;
            for (SiteGuardVisitRegistration registration : registrations) {
                for (SiteGuardVisitPerson person : peopleByRegistration.getOrDefault(registration.getId(), List.of())) {
                    Row row = sheet.createRow(rowNumber++);
                    List<String> values = List.of(
                            nullToEmpty(project.getProjectName()), nullToEmpty(registration.getRegistrationNo()),
                            nullToEmpty(person.getPersonCompany()),
                            VisitorSubmissionNormalizer.PERSON_CONTACT.equals(person.getPersonType()) ? "本人" : "同行人员",
                            nullToEmpty(person.getPersonName()), nullToEmpty(cryptoService.decrypt(person.getPhoneEncrypted())),
                            VisitorSubmissionNormalizer.TRAVEL_DRIVING.equals(registration.getTravelMode()) ? "驾车" : "非驾车",
                            nullToEmpty(registration.getVehiclePlate()), formatDateTime(registration.getRegisteredTime()),
                            formatDateTime(registration.getValidUntil()), statusLabel(effectiveStatus(registration)));
                    for (int index = 0; index < values.size(); index++) {
                        row.createCell(index).setCellValue(safeExcelText(values.get(index)));
                    }
                }
            }
            int[] widths = {24, 22, 24, 12, 14, 18, 12, 16, 18, 18, 12};
            for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("门卫访客登记 Excel 生成失败");
        }
    }

    private SiteGuardVisitQr newQr(Long projectId, int qrVersion, String status, SysUser user) {
        String rawToken = randomSceneToken();
        SiteGuardVisitQr qr = new SiteGuardVisitQr();
        qr.setProjectId(projectId);
        qr.setSceneTokenHash(cryptoService.digest(rawToken));
        qr.setSceneTokenEncrypted(cryptoService.encrypt(rawToken));
        qr.setQrStatus(status);
        qr.setQrVersion(qrVersion);
        qr.setCreatedById(user.getId());
        qr.setCreatedByName(displayName(user));
        qr.setUpdatedById(user.getId());
        qr.setUpdatedByName(displayName(user));
        qr.setVersion(0);
        qr.setDeleted(0);
        qr.setCreateTime(LocalDateTime.now());
        qr.setUpdateTime(LocalDateTime.now());
        return qr;
    }

    private void touchQr(SiteGuardVisitQr qr, SysUser user) {
        qr.setUpdatedById(user.getId());
        qr.setUpdatedByName(displayName(user));
        qr.setVersion(versionOf(qr) + 1);
        qr.setUpdateTime(LocalDateTime.now());
    }

    private SiteGuardVisitQr findCurrentQr(Long projectId) {
        return qrMapper.selectOne(new LambdaQueryWrapper<SiteGuardVisitQr>()
                .eq(SiteGuardVisitQr::getProjectId, projectId)
                .in(SiteGuardVisitQr::getQrStatus, QR_ENABLED, QR_DISABLED)
                .last("LIMIT 1"));
    }

    private SiteGuardVisitQr requireQr(Long id, boolean lock) {
        SiteGuardVisitQr qr = id == null ? null : (lock ? qrMapper.selectForUpdate(id) : qrMapper.selectById(id));
        if (qr == null) throw BusinessException.notFound("门卫登记码不存在");
        return qr;
    }

    private SiteGuardVisitRegistration requireRegistration(Long id) {
        SiteGuardVisitRegistration registration = id == null ? null : registrationMapper.selectById(id);
        if (registration == null) throw BusinessException.notFound("门卫访客登记不存在");
        return registration;
    }

    private void requireCurrentQr(SiteGuardVisitQr qr) {
        if (QR_ROTATED.equals(qr.getQrStatus())) throw stateConflict("门卫登记码已轮换，请刷新后重试");
    }

    private void requireVersion(SiteGuardVisitQr qr, Integer expectedVersion) {
        if (expectedVersion == null || !Objects.equals(versionOf(qr), expectedVersion)) {
            throw stateConflict("门卫登记码已更新，请刷新后重试");
        }
    }

    private void requireVersion(SiteGuardVisitRegistration registration, Integer expectedVersion) {
        if (expectedVersion == null || !Objects.equals(versionOf(registration), expectedVersion)) {
            throw stateConflict("门卫访客登记已更新，请刷新后重试");
        }
    }

    private SiteGuardVisitQrVO toQrVO(SiteGuardVisitQr qr, ProjectInfo project) {
        SiteGuardVisitQrVO vo = new SiteGuardVisitQrVO();
        vo.setId(qr.getId());
        vo.setProjectId(qr.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setQrStatus(qr.getQrStatus());
        vo.setQrVersion(qr.getQrVersion());
        vo.setVersion(qr.getVersion());
        vo.setPagePath(miniProgramPage);
        vo.setCreateTime(qr.getCreateTime());
        vo.setUpdateTime(qr.getUpdateTime());
        return vo;
    }

    private String normalizeSceneToken(String raw) {
        String token = VisitorSubmissionNormalizer.requiredText(raw, 64, "门卫登记令牌");
        if (token.startsWith("G:")) token = token.substring(2);
        if (!token.matches("^[A-Za-z0-9_-]{20,40}$")) throw BusinessException.notFound("门卫登记入口不存在");
        return token;
    }

    private String randomSceneToken() {
        byte[] value = new byte[20];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String guardIdentityHash(VisitorSessionService.VisitorSessionContext context) {
        return cryptoService.fingerprint("site-access:guard-registration:v1",
                context.appId() + ":" + sessionService.decryptOpenid(context));
    }

    private String generateRegistrationNo(LocalDateTime now) {
        return "GVR-" + DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String effectiveStatus(SiteGuardVisitRegistration registration) {
        if (STATUS_REGISTERED.equals(registration.getStatus()) && registration.getValidUntil() != null
                && !registration.getValidUntil().isAfter(LocalDateTime.now())) return STATUS_EXPIRED;
        return registration.getStatus();
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(STATUS_REGISTERED, STATUS_VOIDED, STATUS_EXPIRED).contains(value)) {
            throw new BusinessException("门卫访客登记状态不正确");
        }
        return value;
    }

    private ProjectInfo requireProject(Long projectId) {
        ProjectInfo project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    private ProjectInfo requireProjectForUpdate(Long projectId) {
        ProjectInfo project = projectId == null ? null : projectMapper.selectByIdForUpdate(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    private ProjectInfo requireUsableProject(Long projectId) {
        ProjectInfo project = requireProject(projectId);
        ensureProjectActive(project);
        return project;
    }

    private void ensureProjectActive(ProjectInfo project) {
        if ("stopped".equalsIgnoreCase(project.getProjectStatus())) {
            throw BusinessException.of(410, "项目已停用，门卫登记入口不可用");
        }
    }

    private void requirePermission(SysUser user, Long projectId, String permission) {
        if (projectId == null || projectId <= 0) throw new BusinessException("项目ID不能为空");
        permissionService.requireSystemPermission(user.getId(), projectId, permission);
    }

    private DateRange normalizeOptionalRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return null;
        if (startDate == null || endDate == null) throw new BusinessException("请选择完整的开始日期和结束日期");
        return validateRange(startDate, endDate);
    }

    private DateRange requireExportRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) throw new BusinessException("导出必须选择开始日期和结束日期");
        return validateRange(startDate, endDate);
    }

    private DateRange validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) throw new BusinessException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > 366) throw new BusinessException("日期范围不能超过366天");
        return new DateRange(startDate, endDate);
    }

    private void recordOperation(SysUser user, String action, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(action);
        log.setOperationDesc(description);
        log.setBusinessType("SITE_ACCESS_GUARD");
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        requireSingle(operationLogMapper.insert(log), "系统操作日志写入");
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : Objects.toString(user.getUsername(), "-");
    }

    private int versionOf(SiteGuardVisitQr qr) {
        return qr.getVersion() == null ? 0 : qr.getVersion();
    }

    private int versionOf(SiteGuardVisitRegistration registration) {
        return registration.getVersion() == null ? 0 : registration.getVersion();
    }

    private String safeExcelText(String value) {
        if (value == null) return "";
        String stripped = value.stripLeading();
        return !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0 ? "'" + value : value;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case STATUS_REGISTERED -> "有效";
            case STATUS_EXPIRED -> "已过期";
            case STATUS_VOIDED -> "已作废";
            default -> status;
        };
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DISPLAY_DATE_TIME.format(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeFileName(String value) {
        String result = Objects.toString(value, "项目").replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        return result.isEmpty() ? "项目" : result;
    }

    private void requireSingle(int affected, String action) {
        if (affected != 1) throw stateConflict(action + "状态已变化，请刷新后重试");
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    public record ExportFile(String fileName, byte[] content) {
    }
}
