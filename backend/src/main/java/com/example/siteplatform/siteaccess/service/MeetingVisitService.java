package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectCoordinateConverter;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitorSessionRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingVisitRegistrationUpdateRequest;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitPerson;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingAttendanceMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.vo.PublicMeetingVisitPassVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.PublicProjectLocationVO;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitAuditVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitPersonVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingVisitRegistrationVO;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MeetingVisitService {
    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_VOIDED = "VOIDED";
    public static final String PAGE_FORM = "FORM";
    public static final String PAGE_REGISTERED = "REGISTERED";
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SiteMeetingVisitRegistrationMapper registrationMapper;
    private final SiteMeetingVisitPersonMapper personMapper;
    private final SiteMeetingVisitAuditLogMapper auditMapper;
    private final SiteMeetingAttendanceMapper attendanceMapper;
    private final SiteVisitInvitationMapper invitationMapper;
    private final ProjectInfoMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final ProjectRouteImageService routeImageService;
    private final VisitorDataCryptoService cryptoService;
    private final VisitorSessionService sessionService;
    private final VisitorProfileService profileService;
    private final VisitorPersonalProfileService personalProfileService;
    private final SiteAccessService siteAccessService;
    private final RedisRateLimitService rateLimitService;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MeetingVisitService(SiteMeetingVisitRegistrationMapper registrationMapper,
                               SiteMeetingVisitPersonMapper personMapper,
                               SiteMeetingVisitAuditLogMapper auditMapper,
                               SiteMeetingAttendanceMapper attendanceMapper,
                               SiteVisitInvitationMapper invitationMapper,
                               ProjectInfoMapper projectMapper,
                               ProjectPermissionService permissionService,
                               ProjectRouteImageService routeImageService,
                               VisitorDataCryptoService cryptoService,
                               VisitorSessionService sessionService,
                               VisitorProfileService profileService,
                               VisitorPersonalProfileService personalProfileService,
                               SiteAccessService siteAccessService,
                               RedisRateLimitService rateLimitService,
                               OperationLogMapper operationLogMapper,
                               ObjectMapper objectMapper,
                               TransactionTemplate transactionTemplate) {
        this.registrationMapper = registrationMapper;
        this.personMapper = personMapper;
        this.auditMapper = auditMapper;
        this.attendanceMapper = attendanceMapper;
        this.invitationMapper = invitationMapper;
        this.projectMapper = projectMapper;
        this.permissionService = permissionService;
        this.routeImageService = routeImageService;
        this.cryptoService = cryptoService;
        this.sessionService = sessionService;
        this.profileService = profileService;
        this.personalProfileService = personalProfileService;
        this.siteAccessService = siteAccessService;
        this.rateLimitService = rateLimitService;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public PublicMeetingVisitorSessionVO createPublicSession(PublicMeetingVisitorSessionRequest request) {
        if (request == null) throw new BusinessException("会议登记会话参数不能为空");
        SiteVisitInvitation invitation = resolveInvitation(request.getInviteToken());
        validateOpen(invitation);
        requireUsableProject(invitation.getProjectId());
        PublicVisitorSessionVO issued = sessionService.issueMeeting(
                request.getWechatCode(), invitation.getId(), invitation.getProjectId());
        PublicMeetingVisitorSessionVO result = transactionTemplate.execute(status ->
                completePublicSession(request.getInviteToken(), invitation, issued));
        if (result == null) throw new IllegalStateException("会议登记会话确认失败");
        return result;
    }

    private PublicMeetingVisitorSessionVO completePublicSession(String inviteToken,
                                                                SiteVisitInvitation resolvedInvitation,
                                                                PublicVisitorSessionVO issued) {
        SiteVisitInvitation invitation = invitationMapper.selectForUpdate(resolvedInvitation.getId());
        if (invitation == null || !Objects.equals(invitation.getProjectId(), resolvedInvitation.getProjectId())) {
            throw BusinessException.notFound("会议邀请不存在或已失效");
        }
        validateOpen(invitation);
        ProjectInfo project = requireUsableProject(invitation.getProjectId());
        VisitorSessionService.VisitorSessionContext context = sessionService.requireMeeting(
                issued.getVisitorSessionToken(), invitation.getId(), invitation.getProjectId());
        SiteMeetingVisitRegistration active = findActive(context);
        PublicSiteVisitInvitationVO publicInvitation = siteAccessService.resolvePublic(inviteToken);
        PublicMeetingVisitorSessionVO vo = new PublicMeetingVisitorSessionVO();
        vo.setVisitorSessionToken(issued.getVisitorSessionToken());
        vo.setExpiresInSeconds(issued.getExpiresInSeconds());
        vo.setPageState(active == null ? PAGE_FORM : PAGE_REGISTERED);
        vo.setInvitation(publicInvitation);
        if (active != null) vo.setRegistration(toPass(active, invitation, project, LocalDateTime.now()));
        else vo.setPersonalInfo(personalProfileService.read(context));
        // The WeChat exchange and pass assembly can take measurable time. Check the
        // latest deadline again while the invitation row is still locked so an event
        // that expired during this request never returns a stale pass or navigation.
        validateOpen(invitation);
        return vo;
    }

    @Transactional
    public PublicMeetingVisitPassVO submitPublic(PublicMeetingVisitSubmitRequest request,
                                                 String visitorSessionToken) {
        if (request == null) throw new BusinessException("会议访客登记参数不能为空");
        VisitorSessionService.VisitorSessionContext initial = sessionService.require(visitorSessionToken);
        if (!VisitorSessionService.SOURCE_MEETING_INVITATION.equals(initial.effectiveSourceType())) {
            throw BusinessException.of(403, "当前访客会话不能用于会议登记");
        }
        String identityHash = meetingIdentityHash(initial);
        rateLimitService.check("public-site-meeting-submit-identity", identityHash,
                10, Duration.ofMinutes(30));
        ProjectInfo project = requireProjectForUpdate(initial.projectId());
        ensureProjectActive(project);
        SiteVisitInvitation invitation = invitationMapper.selectForUpdate(initial.effectiveSourceId());
        validateOpen(invitation);
        VisitorSessionService.VisitorSessionContext context = sessionService.requireMeeting(
                visitorSessionToken, invitation.getId(), invitation.getProjectId());
        SiteMeetingVisitRegistration existing = registrationMapper.selectActiveForUpdate(
                invitation.getId(), context.appId(), identityHash);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) return toPass(existing, invitation, project, now);

        VisitorSubmissionNormalizer.Submission submission = VisitorSubmissionNormalizer.normalize(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getCompanions(), request.getTravelMode(), request.getVehiclePlate(),
                request.getVisitorRemark());
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) throw new BusinessException("请阅读并同意隐私告知");
        Long sourceProfileId = profileService.applyOnSubmission(
                context, request.getProfileAction(), request.getProfileCode(), request.getProfileName(),
                request.getProfileRetentionAgreed(), request.getProfileVersion(), toProfileSubmission(submission));

        personalProfileService.saveOnSubmission(context, request.getRememberInfo(), submission);
        SiteMeetingVisitRegistration registration = createRegistration(invitation, context, submission, sourceProfileId, now);
        return toPass(registration, invitation, project, now);
    }

    private SiteMeetingVisitRegistration createRegistration(SiteVisitInvitation invitation,
            VisitorSessionService.VisitorSessionContext context, VisitorSubmissionNormalizer.Submission submission,
            Long sourceProfileId, LocalDateTime now) {
        String identityHash = meetingIdentityHash(context);
        SiteMeetingVisitRegistration registration = new SiteMeetingVisitRegistration();
        registration.setRegistrationNo(generateRegistrationNo(now));
        registration.setInvitationId(invitation.getId());
        registration.setProjectId(invitation.getProjectId());
        registration.setWechatAppId(context.appId());
        registration.setVisitorIdentityHash(identityHash);
        registration.setStatus(STATUS_REGISTERED);
        registration.setRegistrationSource(MeetingCheckinService.SOURCE_INVITATION);
        applySubmission(registration, submission);
        registration.setSourceProfileId(sourceProfileId);
        registration.setPrivacyAgreedTime(now);
        registration.setRegisteredTime(now);
        registration.setVersion(0);
        registration.setDeleted(0);
        registration.setCreateTime(now);
        registration.setUpdateTime(now);
        try {
            requireSingle(registrationMapper.insert(registration), "会议访客登记");
        } catch (DuplicateKeyException duplicate) {
            SiteMeetingVisitRegistration raced = registrationMapper.selectActiveForUpdate(
                    invitation.getId(), context.appId(), identityHash);
            if (raced != null) return raced;
            throw stateConflict("会议访客登记冲突，请重试");
        }
        replacePeople(registration, submission);
        writeAudit(registration, "REGISTER", null, null, snapshot(registration), "访客扫码完成会议登记");
        return registration;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public SiteMeetingVisitRegistration registerFromGuard(VisitorSessionService.VisitorSessionContext context,
            GuardMeetingChoiceService.Choice choice, VisitorSubmissionNormalizer.Submission submission, Long sourceProfileId) {
        if (!VisitorSessionService.SOURCE_GUARD_QR.equals(context.effectiveSourceType()))
            throw BusinessException.of(403, "当前会话不能进行门卫会议预约");
        SiteVisitInvitation invitation = invitationMapper.selectForUpdate(choice.invitationId());
        validateOpen(invitation);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        if (!Objects.equals(invitation.getProjectId(), context.projectId())) throw BusinessException.of(403, "会议不属于当前项目");
        if (!Objects.equals(invitation.getVersion(), choice.version())
                || !invitation.getVisitStartTime().isBefore(now.toLocalDate().plusDays(7).atStartOfDay()))
            throw BusinessException.of(409, "会议已调整，请刷新列表后重新选择");
        SiteMeetingVisitRegistration existing = registrationMapper.selectActiveForUpdate(
                invitation.getId(), context.appId(), meetingIdentityHash(context));
        if (existing != null) return existing;
        return createRegistration(invitation, context, submission, sourceProfileId, now);
    }

    public List<SiteVisitorProfileVO> publicProfiles(String token) {
        VisitorSessionService.VisitorSessionContext context = requireMeetingContext(token);
        checkIdentityRateLimit("public-site-meeting-profile-list-identity", context,
                60, Duration.ofMinutes(10));
        return profileService.publicList(context);
    }

    public SiteVisitorProfileVO publicProfile(String token, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requireMeetingContext(token);
        checkIdentityRateLimit("public-site-meeting-profile-detail-identity", context,
                60, Duration.ofMinutes(10));
        return profileService.publicDetail(context, profileCode);
    }

    public void disablePublicProfile(String token, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requireMeetingContext(token);
        checkIdentityRateLimit("public-site-meeting-profile-disable-identity", context,
                10, Duration.ofMinutes(30));
        profileService.publicDisable(context, profileCode);
    }

    public PageResult<SiteMeetingVisitRegistrationVO> page(Long invitationId, String status, String keyword,
                                                            Integer pageNo, Integer pageSize, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        Page<SiteMeetingVisitRegistration> result = registrationMapper.selectPage(
                new Page<>(current, size), query(invitationId, invitation.getProjectId(), status, keyword, null));
        ProjectInfo project = requireProject(invitation.getProjectId());
        return PageResult.of(current, size, result.getTotal(), result.getRecords().stream()
                .map(item -> toVO(item, invitation, project, false)).toList());
    }

    public SiteMeetingVisitRegistrationVO detail(Long id, SysUser user) {
        SiteMeetingVisitRegistration registration = requireRegistration(id);
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), false);
        return toVO(registration, invitation, requireProject(registration.getProjectId()), true);
    }

    @Transactional
    public SiteMeetingVisitRegistrationVO update(Long id, SiteMeetingVisitRegistrationUpdateRequest request,
                                                 SysUser user) {
        if (request == null) throw new BusinessException("纠错参数不能为空");
        SiteMeetingVisitRegistration registration = registrationMapper.selectForUpdate(id);
        if (registration == null) throw BusinessException.notFound("会议访客登记不存在");
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), true);
        validateOpen(invitation);
        if (!STATUS_REGISTERED.equals(registration.getStatus())) throw stateConflict("已作废登记不能纠错");
        ensureNoActiveAttendance(registration.getId());
        requireVersion(registration, request.getVersion());
        VisitorSubmissionNormalizer.Submission submission = VisitorSubmissionNormalizer.normalize(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getCompanions(), request.getTravelMode(), request.getVehiclePlate(), request.getVisitorRemark());
        Map<String, Object> before = snapshot(registration);
        applySubmission(registration, submission);
        replacePeople(registration, submission);
        registration.setVersion(versionOf(registration) + 1);
        registration.setUpdateTime(LocalDateTime.now());
        requireSingle(registrationMapper.updateById(registration), "会议访客登记纠错");
        writeAudit(registration, "UPDATE", user, before, snapshot(registration), "后台纠错会议访客登记");
        recordOperation(user, "UPDATE_MEETING_VISIT", registration.getId(),
                "纠错会议访客登记 " + registration.getRegistrationNo());
        return toVO(registration, invitation, requireProject(registration.getProjectId()), true);
    }

    @Transactional
    public SiteMeetingVisitRegistrationVO voidRegistration(Long id, String reason, Integer expectedVersion,
                                                           SysUser user) {
        SiteMeetingVisitRegistration registration = registrationMapper.selectForUpdate(id);
        if (registration == null) throw BusinessException.notFound("会议访客登记不存在");
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), true);
        validateOpen(invitation);
        if (STATUS_VOIDED.equals(registration.getStatus())) throw stateConflict("会议访客登记已经作废");
        ensureNoActiveAttendance(registration.getId());
        requireVersion(registration, expectedVersion);
        String normalizedReason = VisitorSubmissionNormalizer.requiredText(reason, 300, "作废原因");
        Map<String, Object> before = snapshot(registration);
        registration.setStatus(STATUS_VOIDED);
        registration.setVoidReason(normalizedReason);
        registration.setVoidedById(user.getId());
        registration.setVoidedByName(displayName(user));
        registration.setVoidedTime(LocalDateTime.now());
        registration.setVersion(versionOf(registration) + 1);
        registration.setUpdateTime(LocalDateTime.now());
        requireSingle(registrationMapper.updateById(registration), "会议访客登记作废");
        writeAudit(registration, "VOID", user, before, snapshot(registration), normalizedReason);
        recordOperation(user, "VOID_MEETING_VISIT", registration.getId(),
                "作废会议访客登记 " + registration.getRegistrationNo());
        return toVO(registration, invitation, requireProject(registration.getProjectId()), true);
    }

    public ExportFile export(Long projectId, Long invitationId, String status, String meetingStatus,
                             String keyword, LocalDate startDate, LocalDate endDate, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_EXPORT);
        DateRange range = invitationId == null ? requireRange(startDate, endDate) : null;
        SiteVisitInvitation selected = invitationId == null ? null : requireMeetingInvitation(invitationId, false);
        if (selected != null && !Objects.equals(selected.getProjectId(), projectId)) {
            throw BusinessException.of(403, "会议邀请不属于当前项目");
        }
        List<SiteMeetingVisitRegistration> registrations = registrationMapper.selectList(
                query(invitationId, projectId, status, keyword, range));
        List<Long> invitationIds = registrations.stream().map(SiteMeetingVisitRegistration::getInvitationId)
                .distinct().toList();
        Map<Long, SiteVisitInvitation> invitations = invitationIds.isEmpty() ? Map.of()
                : invitationMapper.selectBatchIds(invitationIds).stream()
                .collect(Collectors.toMap(SiteVisitInvitation::getId, item -> item));
        if (StringUtils.hasText(meetingStatus)) {
            String normalizedMeetingStatus = normalizeMeetingStatus(meetingStatus);
            LocalDateTime now = LocalDateTime.now();
            registrations = registrations.stream().filter(registration ->
                    normalizedMeetingStatus.equals(effectiveMeetingStatus(
                            invitations.get(registration.getInvitationId()), now))).toList();
        }
        List<Long> registrationIds = registrations.stream().map(SiteMeetingVisitRegistration::getId).toList();
        Map<Long, List<SiteMeetingVisitPerson>> people = registrationIds.isEmpty() ? Map.of()
                : personMapper.selectList(new LambdaQueryWrapper<SiteMeetingVisitPerson>()
                .in(SiteMeetingVisitPerson::getRegistrationId, registrationIds)
                .orderByAsc(SiteMeetingVisitPerson::getRegistrationId)
                .orderByAsc(SiteMeetingVisitPerson::getSortOrder)).stream()
                .collect(Collectors.groupingBy(SiteMeetingVisitPerson::getRegistrationId,
                        LinkedHashMap::new, Collectors.toList()));
        int rows = people.values().stream().mapToInt(List::size).sum();
        if (rows > MAX_EXPORT_ROWS) throw new BusinessException("导出人员超过50000人，请缩小日期范围");
        ProjectInfo project = requireProject(projectId);
        byte[] content = buildWorkbook(project, registrations, people, invitations);
        writeExportAudit(projectId, invitationId, user, rows, range);
        recordOperation(user, "EXPORT_MEETING_VISIT", invitationId == null ? projectId : invitationId,
                "导出会议访客登记，共" + rows + "人");
        String projectName = safeFileName(StringUtils.hasText(project.getShortName())
                ? project.getShortName() : project.getProjectName());
        String suffix = invitationId == null
                ? FILE_DATE.format(range.start()) + "-" + FILE_DATE.format(range.end())
                : safeFileName(selected.getInviteNo());
        return new ExportFile("场内管理_会议登记_" + projectName + "_" + suffix + ".xlsx", content);
    }

    private VisitorSessionService.VisitorSessionContext requireMeetingContext(String token) {
        VisitorSessionService.VisitorSessionContext context = sessionService.require(token);
        if (!VisitorSessionService.SOURCE_MEETING_INVITATION.equals(context.effectiveSourceType())) {
            throw BusinessException.of(403, "当前访客会话不能用于会议登记");
        }
        SiteVisitInvitation invitation = requireMeetingInvitation(context.effectiveSourceId(), false);
        validateOpen(invitation);
        requireUsableProject(invitation.getProjectId());
        return sessionService.requireMeeting(token, invitation.getId(), invitation.getProjectId());
    }

    private SiteVisitInvitation resolveInvitation(String rawToken) {
        String token = VisitorSubmissionNormalizer.requiredText(rawToken, 64, "会议邀请令牌");
        if (token.startsWith("M:")) token = token.substring(2);
        if (!token.matches("^[A-Za-z0-9_-]{20,32}$")) throw BusinessException.notFound("会议邀请不存在或已失效");
        SiteVisitInvitation invitation = invitationMapper.selectOne(new LambdaQueryWrapper<SiteVisitInvitation>()
                .eq(SiteVisitInvitation::getTokenHash, cryptoService.digest(token)).last("LIMIT 1"));
        if (invitation == null) throw BusinessException.notFound("会议邀请不存在或已失效");
        if (!SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) {
            throw BusinessException.of(403, "当前二维码不是会议邀请");
        }
        return invitation;
    }

    private SiteMeetingVisitRegistration findActive(VisitorSessionService.VisitorSessionContext context) {
        return registrationMapper.selectOne(new LambdaQueryWrapper<SiteMeetingVisitRegistration>()
                .eq(SiteMeetingVisitRegistration::getInvitationId, context.effectiveSourceId())
                .eq(SiteMeetingVisitRegistration::getWechatAppId, context.appId())
                .eq(SiteMeetingVisitRegistration::getVisitorIdentityHash, meetingIdentityHash(context))
                .eq(SiteMeetingVisitRegistration::getStatus, STATUS_REGISTERED)
                .orderByDesc(SiteMeetingVisitRegistration::getRegisteredTime)
                .orderByDesc(SiteMeetingVisitRegistration::getId).last("LIMIT 1"));
    }

    private LambdaQueryWrapper<SiteMeetingVisitRegistration> query(Long invitationId, Long projectId,
                                                                    String status, String keyword, DateRange range) {
        LambdaQueryWrapper<SiteMeetingVisitRegistration> query = new LambdaQueryWrapper<SiteMeetingVisitRegistration>()
                .eq(SiteMeetingVisitRegistration::getProjectId, projectId)
                .orderByDesc(SiteMeetingVisitRegistration::getRegisteredTime)
                .orderByDesc(SiteMeetingVisitRegistration::getId);
        if (invitationId != null) query.eq(SiteMeetingVisitRegistration::getInvitationId, invitationId);
        if (StringUtils.hasText(status)) query.eq(SiteMeetingVisitRegistration::getStatus, normalizeStatus(status));
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("查询关键词不能超过100个字符");
            query.and(item -> item.like(SiteMeetingVisitRegistration::getRegistrationNo, value)
                    .or().like(SiteMeetingVisitRegistration::getVisitorCompany, value)
                    .or().like(SiteMeetingVisitRegistration::getContactName, value)
                    .or().like(SiteMeetingVisitRegistration::getVehiclePlate, value));
        }
        if (range != null) query.ge(SiteMeetingVisitRegistration::getRegisteredTime, range.start().atStartOfDay())
                .lt(SiteMeetingVisitRegistration::getRegisteredTime, range.end().plusDays(1).atStartOfDay());
        return query;
    }

    private void applySubmission(SiteMeetingVisitRegistration registration,
                                 VisitorSubmissionNormalizer.Submission submission) {
        registration.setVisitorCompany(submission.visitorCompany());
        registration.setContactName(submission.contactName());
        registration.setContactPhoneEncrypted(cryptoService.encrypt(submission.contactPhone()));
        registration.setVisitorCount(submission.people().size());
        registration.setTravelMode(submission.travelMode());
        registration.setVehiclePlate(submission.vehiclePlate());
        registration.setVisitorRemark(submission.visitorRemark());
    }

    private void replacePeople(SiteMeetingVisitRegistration registration,
                               VisitorSubmissionNormalizer.Submission submission) {
        personMapper.delete(new LambdaQueryWrapper<SiteMeetingVisitPerson>()
                .eq(SiteMeetingVisitPerson::getRegistrationId, registration.getId()));
        int order = 1;
        for (VisitorSubmissionNormalizer.Person value : submission.people()) {
            SiteMeetingVisitPerson person = new SiteMeetingVisitPerson();
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
            requireSingle(personMapper.insert(person), "会议登记人员写入");
        }
    }

    private VisitorProfileService.SubmissionData toProfileSubmission(
            VisitorSubmissionNormalizer.Submission submission) {
        return new VisitorProfileService.SubmissionData(
                submission.visitorCompany(), submission.contactName(), submission.contactPhone(),
                submission.travelMode(), submission.vehiclePlate(), submission.people().stream()
                .map(person -> new VisitorProfileService.PersonData(
                        person.personType(), person.personCompany(), person.personName(), person.personPhone())).toList());
    }

    private PublicMeetingVisitPassVO toPass(SiteMeetingVisitRegistration registration,
                                            SiteVisitInvitation invitation, ProjectInfo project,
                                            LocalDateTime serverTime) {
        PublicMeetingVisitPassVO vo = new PublicMeetingVisitPassVO();
        vo.setRegistrationNo(registration.getRegistrationNo());
        vo.setStatus(registration.getStatus());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setPurpose(invitation.getPurpose());
        vo.setVisitLocation(invitation.getVisitLocation());
        vo.setHostName(invitation.getHostName());
        vo.setVisitorCompany(registration.getVisitorCompany());
        vo.setContactName(registration.getContactName());
        vo.setVisitorCount(registration.getVisitorCount());
        vo.setTravelMode(registration.getTravelMode());
        vo.setVehiclePlate(registration.getVehiclePlate());
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setValidUntil(invitation.getVisitEndTime());
        vo.setRegisteredTime(registration.getRegisteredTime());
        vo.setServerTime(serverTime);
        vo.setVisitors(people(registration.getId()).stream().map(this::toPersonVO).toList());
        vo.setProjectLocation(publicLocation(project));
        return vo;
    }

    private SiteMeetingVisitRegistrationVO toVO(SiteMeetingVisitRegistration registration,
                                                SiteVisitInvitation invitation, ProjectInfo project,
                                                boolean detail) {
        SiteMeetingVisitRegistrationVO vo = new SiteMeetingVisitRegistrationVO();
        vo.setId(registration.getId());
        vo.setRegistrationNo(registration.getRegistrationNo());
        vo.setInvitationId(registration.getInvitationId());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setProjectId(registration.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setMeetingTopic(invitation.getPurpose());
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setVisitEndTime(invitation.getVisitEndTime());
        vo.setStatus(registration.getStatus());
        vo.setVisitorCompany(registration.getVisitorCompany());
        vo.setContactName(registration.getContactName());
        vo.setVisitorCount(registration.getVisitorCount());
        vo.setTravelMode(registration.getTravelMode());
        vo.setVehiclePlate(registration.getVehiclePlate());
        vo.setSourceProfileName(profileService.sourceName(registration.getSourceProfileId()));
        vo.setRegisteredTime(registration.getRegisteredTime());
        vo.setVoidReason(registration.getVoidReason());
        vo.setVoidedByName(registration.getVoidedByName());
        vo.setVoidedTime(registration.getVoidedTime());
        vo.setVersion(registration.getVersion());
        vo.setCreateTime(registration.getCreateTime());
        vo.setUpdateTime(registration.getUpdateTime());
        if (detail) {
            vo.setContactPhone(cryptoService.decrypt(registration.getContactPhoneEncrypted()));
            vo.setVisitorRemark(registration.getVisitorRemark());
            vo.setVisitors(people(registration.getId()).stream().map(this::toPersonVO).toList());
            vo.setAuditLogs(auditMapper.selectList(new LambdaQueryWrapper<SiteMeetingVisitAuditLog>()
                    .eq(SiteMeetingVisitAuditLog::getRegistrationId, registration.getId())
                    .orderByAsc(SiteMeetingVisitAuditLog::getCreateTime)
                    .orderByAsc(SiteMeetingVisitAuditLog::getId)).stream().map(this::toAuditVO).toList());
        }
        return vo;
    }

    private List<SiteMeetingVisitPerson> people(Long registrationId) {
        return personMapper.selectList(new LambdaQueryWrapper<SiteMeetingVisitPerson>()
                .eq(SiteMeetingVisitPerson::getRegistrationId, registrationId)
                .orderByAsc(SiteMeetingVisitPerson::getSortOrder)
                .orderByAsc(SiteMeetingVisitPerson::getId));
    }

    private SiteGuardVisitPersonVO toPersonVO(SiteMeetingVisitPerson person) {
        SiteGuardVisitPersonVO vo = new SiteGuardVisitPersonVO();
        vo.setPersonType(person.getPersonType());
        vo.setPersonCompany(person.getPersonCompany());
        vo.setPersonName(person.getPersonName());
        vo.setPersonPhone(cryptoService.decrypt(person.getPhoneEncrypted()));
        vo.setSortOrder(person.getSortOrder());
        return vo;
    }

    private SiteGuardVisitAuditVO toAuditVO(SiteMeetingVisitAuditLog audit) {
        SiteGuardVisitAuditVO vo = new SiteGuardVisitAuditVO();
        vo.setActionType(audit.getActionType());
        vo.setOperatorName(audit.getOperatorName());
        vo.setComment(audit.getComment());
        vo.setCreateTime(audit.getCreateTime());
        return vo;
    }

    private Map<String, Object> snapshot(SiteMeetingVisitRegistration registration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registrationNo", registration.getRegistrationNo());
        result.put("status", registration.getStatus());
        result.put("visitorCompany", registration.getVisitorCompany());
        result.put("contactName", registration.getContactName());
        result.put("contactPhone", cryptoService.decrypt(registration.getContactPhoneEncrypted()));
        result.put("visitorCount", registration.getVisitorCount());
        result.put("travelMode", registration.getTravelMode());
        result.put("vehiclePlate", registration.getVehiclePlate());
        result.put("visitorRemark", registration.getVisitorRemark());
        result.put("registeredTime", registration.getRegisteredTime());
        result.put("voidReason", registration.getVoidReason());
        result.put("visitors", people(registration.getId()).stream().map(person -> Map.of(
                "personType", Objects.toString(person.getPersonType(), ""),
                "personCompany", Objects.toString(person.getPersonCompany(), ""),
                "personName", Objects.toString(person.getPersonName(), ""),
                "personPhone", Objects.toString(cryptoService.decrypt(person.getPhoneEncrypted()), ""),
                "sortOrder", person.getSortOrder())).toList());
        return result;
    }

    private void writeAudit(SiteMeetingVisitRegistration registration, String action, SysUser operator,
                            Map<String, Object> before, Map<String, Object> after, String comment) {
        SiteMeetingVisitAuditLog audit = new SiteMeetingVisitAuditLog();
        audit.setRegistrationId(registration.getId());
        audit.setInvitationId(registration.getInvitationId());
        audit.setProjectId(registration.getProjectId());
        audit.setActionType(action);
        audit.setOperatorId(operator == null ? null : operator.getId());
        audit.setOperatorName(operator == null ? "外访人员" : displayName(operator));
        audit.setBeforeSnapshotEncrypted(encryptSnapshot(before));
        audit.setAfterSnapshotEncrypted(encryptSnapshot(after));
        audit.setComment(VisitorSubmissionNormalizer.optionalText(comment, 500, "审计说明"));
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "会议登记审计日志写入");
    }

    private void writeExportAudit(Long projectId, Long invitationId, SysUser user, int rows, DateRange range) {
        SiteMeetingVisitAuditLog audit = new SiteMeetingVisitAuditLog();
        audit.setInvitationId(invitationId);
        audit.setProjectId(projectId);
        audit.setActionType("EXPORT");
        audit.setOperatorId(user.getId());
        audit.setOperatorName(displayName(user));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("visitorRows", rows);
        if (range != null) {
            summary.put("startDate", range.start().toString());
            summary.put("endDate", range.end().toString());
        }
        audit.setAfterSnapshotEncrypted(encryptSnapshot(summary));
        audit.setComment("导出会议访客登记，共" + rows + "人");
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "会议登记导出审计写入");
    }

    private String encryptSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会议登记审计快照生成失败", exception);
        }
    }

    private byte[] buildWorkbook(ProjectInfo project, List<SiteMeetingVisitRegistration> registrations,
                                 Map<Long, List<SiteMeetingVisitPerson>> people,
                                 Map<Long, SiteVisitInvitation> invitations) {
        String[] headers = {"项目", "会议邀请编号", "会议主题", "会议开始", "会议截止", "登记编号",
                "单位", "人员类型", "姓名", "手机号码", "出行方式", "车牌号", "接待人", "登记时间", "状态"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("会议访客登记");
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
            for (SiteMeetingVisitRegistration registration : registrations) {
                SiteVisitInvitation invitation = invitations.get(registration.getInvitationId());
                if (invitation == null) continue;
                for (SiteMeetingVisitPerson person : people.getOrDefault(registration.getId(), List.of())) {
                    Row row = sheet.createRow(rowNumber++);
                    List<String> values = List.of(
                            text(project.getProjectName()), text(invitation.getInviteNo()), text(invitation.getPurpose()),
                            format(invitation.getVisitStartTime()), format(invitation.getVisitEndTime()),
                            text(registration.getRegistrationNo()), text(person.getPersonCompany()),
                            VisitorSubmissionNormalizer.PERSON_CONTACT.equals(person.getPersonType()) ? "本人" : "同行人员",
                            text(person.getPersonName()), text(cryptoService.decrypt(person.getPhoneEncrypted())),
                            VisitorSubmissionNormalizer.TRAVEL_DRIVING.equals(registration.getTravelMode()) ? "驾车" : "非驾车",
                            text(registration.getVehiclePlate()), text(invitation.getHostName()),
                            format(registration.getRegisteredTime()),
                            STATUS_REGISTERED.equals(registration.getStatus()) ? "已登记" : "已作废");
                    for (int index = 0; index < values.size(); index++) {
                        row.createCell(index).setCellValue(safeExcelText(values.get(index)));
                    }
                }
            }
            int[] widths = {24, 22, 28, 18, 18, 22, 24, 12, 14, 18, 12, 16, 14, 18, 12};
            for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("会议访客登记 Excel 生成失败");
        }
    }

    private PublicProjectLocationVO publicLocation(ProjectInfo project) {
        PublicProjectLocationVO location = new PublicProjectLocationVO();
        location.setAddress(project.getAddress());
        location.setCoordinateType("GCJ02");
        location.setRouteImageAvailable(routeImageService.hasActiveImage(project.getId()));
        var converted = ProjectCoordinateConverter.toGcj02(
                project.getLongitude(), project.getLatitude(), project.getCoordinateType());
        location.setNavigable(converted.isPresent());
        converted.ifPresent(value -> {
            location.setLongitude(value.longitude());
            location.setLatitude(value.latitude());
        });
        return location;
    }

    private SiteVisitInvitation requireMeetingInvitation(Long id, boolean lock) {
        SiteVisitInvitation invitation = id == null ? null
                : lock ? invitationMapper.selectForUpdate(id) : invitationMapper.selectById(id);
        if (invitation == null || !SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) {
            throw BusinessException.notFound("会议邀请不存在");
        }
        return invitation;
    }

    private SiteMeetingVisitRegistration requireRegistration(Long id) {
        SiteMeetingVisitRegistration registration = id == null ? null : registrationMapper.selectById(id);
        if (registration == null) throw BusinessException.notFound("会议访客登记不存在");
        return registration;
    }

    private void validateOpen(SiteVisitInvitation invitation) {
        if (invitation == null || !SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) {
            throw BusinessException.notFound("会议邀请不存在");
        }
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) {
            throw BusinessException.of(410, "会议邀请已作废");
        }
        if (!SiteAccessService.STATUS_OPEN.equals(invitation.getStatus())) {
            throw stateConflict("会议邀请状态不允许登记");
        }
        if (invitation.getVisitEndTime() == null || !invitation.getVisitEndTime().isAfter(LocalDateTime.now())) {
            throw BusinessException.of(410, "会议邀请已过期");
        }
    }

    private ProjectInfo requireProject(Long projectId) {
        ProjectInfo project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) throw BusinessException.notFound("项目不存在");
        return project;
    }

    private ProjectInfo requireProjectForUpdate(Long projectId) {
        ProjectInfo project = projectId == null ? null : projectMapper.selectByIdForUpdate(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) throw BusinessException.notFound("项目不存在");
        return project;
    }

    private ProjectInfo requireUsableProject(Long projectId) {
        ProjectInfo project = requireProject(projectId);
        ensureProjectActive(project);
        return project;
    }

    private void ensureProjectActive(ProjectInfo project) {
        if ("stopped".equalsIgnoreCase(project.getProjectStatus())) {
            throw BusinessException.of(410, "项目已停用，会议登记入口不可用");
        }
    }

    private String meetingIdentityHash(VisitorSessionService.VisitorSessionContext context) {
        return cryptoService.fingerprint("site-access:meeting-registration:v1",
                context.appId() + ":" + sessionService.decryptOpenid(context));
    }

    private void checkIdentityRateLimit(String scope,
                                        VisitorSessionService.VisitorSessionContext context,
                                        int maximum, Duration window) {
        rateLimitService.check(scope, meetingIdentityHash(context), maximum, window);
    }

    private String generateRegistrationNo(LocalDateTime now) {
        return "MVR-" + DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(STATUS_REGISTERED, STATUS_VOIDED).contains(value)) throw new BusinessException("会议登记状态不正确");
        return value;
    }

    private String normalizeMeetingStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(SiteAccessService.STATUS_OPEN, SiteAccessService.STATUS_EXPIRED,
                SiteAccessService.STATUS_VOIDED).contains(value)) {
            throw new BusinessException("会议状态不正确");
        }
        return value;
    }

    private String effectiveMeetingStatus(SiteVisitInvitation invitation, LocalDateTime now) {
        if (invitation == null || !SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) return "";
        if (SiteAccessService.STATUS_OPEN.equals(invitation.getStatus())
                && invitation.getVisitEndTime() != null && !invitation.getVisitEndTime().isAfter(now)) {
            return SiteAccessService.STATUS_EXPIRED;
        }
        return invitation.getStatus();
    }

    private DateRange requireRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) throw new BusinessException("导出必须选择开始日期和结束日期");
        if (start.isAfter(end)) throw new BusinessException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(start, end) + 1 > 366) throw new BusinessException("日期范围不能超过366天");
        return new DateRange(start, end);
    }

    private void requirePermission(SysUser user, Long projectId, String permission) {
        if (projectId == null || projectId <= 0) throw new BusinessException("项目ID不能为空");
        permissionService.requireSystemPermission(user.getId(), projectId, permission);
    }

    private void requireVersion(SiteMeetingVisitRegistration registration, Integer expectedVersion) {
        if (expectedVersion == null || !Objects.equals(versionOf(registration), expectedVersion)) {
            throw stateConflict("会议访客登记已更新，请刷新后重试");
        }
    }

    private void ensureNoActiveAttendance(Long registrationId) {
        if (attendanceMapper.countCheckedInByRegistration(registrationId) > 0) {
            throw stateConflict("该登记组已有有效签到，请先逐人撤销签到后再纠错或作废");
        }
    }

    private int versionOf(SiteMeetingVisitRegistration registration) {
        return registration.getVersion() == null ? 0 : registration.getVersion();
    }

    private void recordOperation(SysUser user, String action, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(action);
        log.setOperationDesc(description);
        log.setBusinessType("SITE_ACCESS_MEETING");
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        requireSingle(operationLogMapper.insert(log), "系统操作日志写入");
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : Objects.toString(user.getUsername(), "-");
    }

    private String safeExcelText(String value) {
        String stripped = text(value).stripLeading();
        return !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0 ? "'" + value : text(value);
    }

    private String safeFileName(String value) {
        String result = text(value).replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        return result.isEmpty() ? "项目" : result;
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DISPLAY_TIME.format(value);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private void requireSingle(int affected, String action) {
        if (affected != 1) throw BusinessException.of(409, action + "状态已变化，请刷新后重试");
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message);
    }

    private record DateRange(LocalDate start, LocalDate end) {}
    public record ExportFile(String fileName, byte[] content) {}
}
