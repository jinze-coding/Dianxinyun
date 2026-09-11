package com.example.siteplatform.siteaccess.service;

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
import com.example.siteplatform.project.service.ProjectCoordinateConverter;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.dto.MeetingCheckinLocationRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinConfirmRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinSessionRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinWalkInRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendanceActionRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendeeUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingCheckinSettingsUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingWalkInCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import com.example.siteplatform.siteaccess.entity.SiteMeetingAttendance;
import com.example.siteplatform.siteaccess.entity.SiteMeetingCheckinQr;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitPerson;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingAttendanceMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingCheckinQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.vo.PublicMeetingCheckinAttendeeVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingCheckinMeetingVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingCheckinReceiptVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingCheckinSessionVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingAttendanceSummaryVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingAttendeeVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingCheckinMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingCheckinSettingsVO;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MeetingCheckinService {
    public static final String QR_ENABLED = "ENABLED";
    public static final String QR_DISABLED = "DISABLED";
    public static final String QR_ROTATED = "ROTATED";
    public static final String SOURCE_INVITATION = "INVITATION";
    public static final String SOURCE_WALK_IN = "WALK_IN";
    public static final String ATTENDANCE_CHECKED_IN = "CHECKED_IN";
    public static final String ATTENDANCE_REVOKED = "REVOKED";
    public static final String METHOD_VENUE_QR = "VENUE_QR";
    public static final String METHOD_STAFF_MANUAL = "STAFF_MANUAL";
    public static final String LOCATION_IN_RANGE = "IN_RANGE";
    public static final String LOCATION_OUT_OF_RANGE = "OUT_OF_RANGE";
    public static final String LOCATION_UNAVAILABLE = "UNAVAILABLE";
    public static final String LOCATION_NO_REFERENCE = "NO_REFERENCE";
    public static final String LOCATION_MANUAL = "MANUAL";
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SiteMeetingCheckinQrMapper qrMapper;
    private final SiteMeetingAttendanceMapper attendanceMapper;
    private final SiteMeetingVisitRegistrationMapper registrationMapper;
    private final SiteMeetingVisitPersonMapper personMapper;
    private final SiteMeetingVisitAuditLogMapper auditMapper;
    private final SiteVisitInvitationMapper invitationMapper;
    private final ProjectInfoMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final VisitorDataCryptoService cryptoService;
    private final VisitorSessionService sessionService;
    private final VisitorProfileService profileService;
    private final VisitorPersonalProfileService personalProfiles;
    private final RedisRateLimitService rateLimitService;
    private final WechatPlatformClient wechatPlatformClient;
    private final OperationLogMapper operationLogMapper;
    private final MeetingCheckinQrProvisioner provisioner;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String miniProgramPage;
    private final String miniProgramEnvVersion;

    public MeetingCheckinService(SiteMeetingCheckinQrMapper qrMapper,
                                 SiteMeetingAttendanceMapper attendanceMapper,
                                 SiteMeetingVisitRegistrationMapper registrationMapper,
                                 SiteMeetingVisitPersonMapper personMapper,
                                 SiteMeetingVisitAuditLogMapper auditMapper,
                                 SiteVisitInvitationMapper invitationMapper,
                                 ProjectInfoMapper projectMapper,
                                 ProjectPermissionService permissionService,
                                 VisitorDataCryptoService cryptoService,
                                 VisitorSessionService sessionService,
                                 VisitorProfileService profileService,
                                 VisitorPersonalProfileService personalProfiles,
                                 RedisRateLimitService rateLimitService,
                                 WechatPlatformClient wechatPlatformClient,
                                 OperationLogMapper operationLogMapper,
                                 MeetingCheckinQrProvisioner provisioner,
                                 ObjectMapper objectMapper,
                                 TransactionTemplate transactionTemplate,
                                 @Value("${wechat.mini-program.meeting-checkin-page:pages/public/meeting-check-in}") String miniProgramPage,
                                 @Value("${wechat.mini-program.env-version:release}") String miniProgramEnvVersion) {
        this.qrMapper = qrMapper;
        this.attendanceMapper = attendanceMapper;
        this.registrationMapper = registrationMapper;
        this.personMapper = personMapper;
        this.auditMapper = auditMapper;
        this.invitationMapper = invitationMapper;
        this.projectMapper = projectMapper;
        this.permissionService = permissionService;
        this.cryptoService = cryptoService;
        this.sessionService = sessionService;
        this.profileService = profileService;
        this.personalProfiles = personalProfiles;
        this.rateLimitService = rateLimitService;
        this.wechatPlatformClient = wechatPlatformClient;
        this.operationLogMapper = operationLogMapper;
        this.provisioner = provisioner;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.miniProgramPage = miniProgramPage;
        this.miniProgramEnvVersion = miniProgramEnvVersion;
    }

    public SiteMeetingCheckinSettingsVO settings(Long invitationId, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        SiteMeetingCheckinQr qr = qrMapper.selectCurrent(invitationId);
        if (qr == null) return null;
        return toSettings(qr, invitation, requireProject(invitation.getProjectId()), LocalDateTime.now());
    }

    @Transactional
    public SiteMeetingCheckinSettingsVO updateSettings(Long invitationId,
                                                        SiteMeetingCheckinSettingsUpdateRequest request,
                                                        SysUser user) {
        if (request == null) throw new BusinessException("签到设置不能为空");
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, true);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ensureMutableMeeting(invitation);
        SiteMeetingCheckinQr qr = requireCurrentQr(invitation, user, true);
        requireVersion(qr, request.getVersion());
        validateWindow(invitation, request.getCheckinStartTime(), request.getCheckinEndTime());
        Map<String, Object> before = qrSnapshot(qr);
        qr.setCheckinStartTime(request.getCheckinStartTime());
        qr.setCheckinEndTime(request.getCheckinEndTime());
        qr.setLocationRadiusMeters(request.getLocationRadiusMeters());
        touchQr(qr, user);
        requireSingle(qrMapper.updateById(qr), "会议签到设置更新");
        writeAudit(invitation, null, null, qr, "CHECKIN_QR_UPDATE", user,
                before, qrSnapshot(qr), "调整签到时间或定位半径");
        recordOperation(user, "UPDATE_MEETING_CHECKIN_QR", invitation.getId(), "调整会议签到设置");
        return toSettings(qr, invitation, requireProject(invitation.getProjectId()), LocalDateTime.now());
    }

    @Transactional
    public SiteMeetingCheckinSettingsVO changeStatus(Long invitationId, boolean enabled, Integer expectedVersion,
                                                      SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, true);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ensureMutableMeeting(invitation);
        SiteMeetingCheckinQr qr = qrMapper.selectCurrentForUpdate(invitation.getId());
        boolean created = qr == null;
        if (created) qr = provisioner.provision(invitation, user);
        requireVersion(qr, expectedVersion);
        String target = enabled ? QR_ENABLED : QR_DISABLED;
        if (!target.equals(qr.getQrStatus())) {
            Map<String, Object> before = qrSnapshot(qr);
            qr.setQrStatus(target);
            touchQr(qr, user);
            requireSingle(qrMapper.updateById(qr), "会议签到码启停");
            writeAudit(invitation, null, null, qr, "CHECKIN_QR_STATUS", user,
                    before, qrSnapshot(qr), enabled ? "启用会议签到码" : "停用会议签到码");
            recordOperation(user, "STATUS_MEETING_CHECKIN_QR", invitation.getId(), enabled ? "启用会议签到码" : "停用会议签到码");
        } else if (created) {
            recordOperation(user, "CREATE_MEETING_CHECKIN_QR", invitation.getId(), "为历史会议创建现场签到码");
        }
        return toSettings(qr, invitation, requireProject(invitation.getProjectId()), LocalDateTime.now());
    }

    @Transactional
    public SiteMeetingCheckinSettingsVO rotate(Long invitationId, Integer expectedVersion, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, true);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ensureMutableMeeting(invitation);
        SiteMeetingCheckinQr current = requireCurrentQr(invitation, user, true);
        requireVersion(current, expectedVersion);
        current.setQrStatus(QR_ROTATED);
        touchQr(current, user);
        requireSingle(qrMapper.updateById(current), "旧会议签到码轮换");

        SiteMeetingCheckinQr replacement = provisioner.provisionReplacement(invitation, current, user);
        recordOperation(user, "ROTATE_MEETING_CHECKIN_QR", invitation.getId(), "轮换会议签到码");
        return toSettings(replacement, invitation, requireProject(invitation.getProjectId()), LocalDateTime.now());
    }

    public SiteMeetingCheckinMiniCodeVO miniCode(Long invitationId, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        SiteMeetingCheckinQr qr = qrMapper.selectCurrent(invitationId);
        if (qr == null) throw BusinessException.notFound("会议签到码尚未创建");
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) throw stateConflict("已作废会议不能下载签到码");
        if (QR_ROTATED.equals(qr.getQrStatus())) throw stateConflict("会议签到码已轮换");
        if (invitation.getVisitEndTime() == null || !invitation.getVisitEndTime().isAfter(LocalDateTime.now())) {
            throw stateConflict("会议已经结束，不能再下载签到码");
        }
        String scene = "MC:" + cryptoService.decrypt(qr.getSceneTokenEncrypted());
        String image = wechatPlatformClient.generateUnlimitedCode(scene, miniProgramPage, miniProgramEnvVersion);
        SiteMeetingCheckinMiniCodeVO vo = new SiteMeetingCheckinMiniCodeVO();
        vo.setInvitationId(invitationId);
        vo.setCheckinQrId(qr.getId());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setQrStatus(qr.getQrStatus());
        vo.setQrVersion(qr.getQrVersion());
        vo.setSceneCode(scene);
        vo.setPagePath(miniProgramPage);
        vo.setCodeType(image == null ? "DEVELOPMENT_SCENE" : "WECHAT_MINI_PROGRAM_CODE");
        vo.setImageMimeType(image == null ? null : "image/png");
        vo.setImageContent(image);
        vo.setHint(image == null ? "当前环境使用 MC: scene 调试会议现场签到"
                : "会场签到专用码；请勿与会前会议邀请登记码混用");
        return vo;
    }

    @Transactional(readOnly = true)
    public com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO screen(Long invitationId, Integer pageNo, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        var result = new com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO();
        var summary = summaryFrom(attendanceMapper.selectSummary(invitationId));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int pages = Math.max(1, (int) ((summary.getTotalCheckedInCount() + 23) / 24));
        page = Math.min(page, pages);
        var rows = attendanceMapper.selectScreenPage(
                new Page<com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO.Person>(page, 24), invitationId);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        result.setInvitationId(invitationId);
        result.setProjectId(invitation.getProjectId());
        result.setTitle(invitation.getPurpose());
        result.setInviteNo(invitation.getInviteNo());
        result.setVisitStartTime(invitation.getVisitStartTime());
        result.setVisitEndTime(invitation.getVisitEndTime());
        result.setServerTime(now);
        result.setMeetingStatus("VOIDED".equals(invitation.getStatus()) ? "VOIDED"
                : !invitation.getVisitEndTime().isAfter(now) ? "ENDED" : "OPEN");
        result.setCanShowQr(permissionService.hasSystemPermission(user.getId(), invitation.getProjectId(),
                SystemPermissionCodes.SITE_ACCESS_MANAGE));
        var qr = qrMapper.selectCurrent(invitationId);
        if (qr != null) { result.setQrStatus(qr.getQrStatus()); result.setQrVersion(qr.getQrVersion()); }
        result.setReservedPersonCount(summary.getReservedPersonCount());
        result.setReservedCheckedInCount(summary.getReservedCheckedInCount());
        result.setReservedPendingCount(summary.getReservedPendingCount());
        result.setWalkInCheckedInCount(summary.getWalkInCheckedInCount());
        result.setTotalCheckedInCount(summary.getTotalCheckedInCount());
        result.setTotal(rows.getTotal()); result.setPageNo(page); result.setPageSize(24);
        result.setRecords(rows.getRecords());
        return result;
    }

    public SiteMeetingAttendanceSummaryVO summary(Long invitationId, SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        Map<String, Object> values = attendanceMapper.selectSummary(invitationId);
        SiteMeetingAttendanceSummaryVO vo = summaryFrom(values);
        SiteMeetingCheckinQr qr = qrMapper.selectCurrent(invitationId);
        if (qr != null) vo.setSettings(toSettings(qr, invitation, requireProject(invitation.getProjectId()), LocalDateTime.now()));
        return vo;
    }

    public PageResult<SiteMeetingAttendeeVO> attendees(Long invitationId, String registrationSource,
                                                       String attendanceStatus, String locationResult,
                                                       String keyword, Integer pageNo, Integer pageSize,
                                                       SysUser user) {
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        String source = normalizeOptional(registrationSource, Set.of(SOURCE_INVITATION, SOURCE_WALK_IN), "登记来源");
        String state = normalizeOptional(attendanceStatus,
                Set.of(ATTENDANCE_CHECKED_IN, ATTENDANCE_REVOKED, "PENDING"), "签到状态");
        String location = normalizeOptional(locationResult,
                Set.of(LOCATION_IN_RANGE, LOCATION_OUT_OF_RANGE, LOCATION_UNAVAILABLE,
                        LOCATION_NO_REFERENCE, LOCATION_MANUAL), "定位结果");
        String normalizedKeyword = optionalText(keyword, 100, "查询关键词");
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        Page<Map<String, Object>> page = attendanceMapper.selectAttendeePage(
                new Page<>(current, size), invitationId, source, state, location, normalizedKeyword);
        return PageResult.of(current, size, page.getTotal(), page.getRecords().stream().map(this::toAttendee).toList());
    }

    public PublicMeetingCheckinSessionVO createPublicSession(PublicMeetingCheckinSessionRequest request) {
        if (request == null) throw new BusinessException("会议签到会话参数不能为空");
        SiteMeetingCheckinQr resolved = resolveQr(request.getSceneToken());
        SiteVisitInvitation invitation = requireMeetingInvitation(resolved.getInvitationId(), false);
        validatePublicCheckin(resolved, invitation, LocalDateTime.now());
        requireUsableProject(resolved.getProjectId());
        PublicVisitorSessionVO issued = sessionService.issueMeetingCheckin(
                request.getWechatCode(), resolved.getId(), resolved.getProjectId());
        PublicMeetingCheckinSessionVO result = transactionTemplate.execute(status ->
                completePublicSession(issued, resolved.getId()));
        if (result == null) throw new IllegalStateException("会议签到会话确认失败");
        return result;
    }

    protected PublicMeetingCheckinSessionVO completePublicSession(PublicVisitorSessionVO issued, Long qrId) {
        SiteMeetingCheckinQr qr = qrMapper.selectForUpdate(qrId);
        if (qr == null) throw BusinessException.notFound("会议签到入口不存在或已失效");
        SiteVisitInvitation invitation = requireMeetingInvitation(qr.getInvitationId(), true);
        LocalDateTime now = LocalDateTime.now();
        validatePublicCheckin(qr, invitation, now);
        ProjectInfo project = requireUsableProject(qr.getProjectId());
        VisitorSessionService.VisitorSessionContext context = sessionService.requireMeetingCheckin(
                issued.getVisitorSessionToken(), qr.getId(), qr.getProjectId());
        SiteMeetingVisitRegistration registration = findActiveRegistration(invitation.getId(), context, false);
        PublicMeetingCheckinSessionVO vo = new PublicMeetingCheckinSessionVO();
        vo.setVisitorSessionToken(issued.getVisitorSessionToken());
        vo.setExpiresInSeconds(issued.getExpiresInSeconds());
        vo.setMeeting(toPublicMeeting(invitation, qr, project, now));
        if (registration == null) {
            vo.setPageState("WALK_IN_FORM");
            vo.setPersonalInfo(personalProfiles.read(context));
        } else {
            List<PublicMeetingCheckinAttendeeVO> people = publicAttendees(registration.getId());
            long checked = people.stream().filter(item -> ATTENDANCE_CHECKED_IN.equals(item.getAttendanceStatus())).count();
            vo.setPageState(checked == people.size() ? "COMPLETED" : checked == 0 ? "RESERVED_PENDING" : "RESERVED_PARTIAL");
            vo.setRegistrationNo(registration.getRegistrationNo());
            vo.setRegistrationSource(sourceOf(registration));
            vo.setAttendees(people);
        }
        validatePublicCheckin(qr, invitation, LocalDateTime.now());
        return vo;
    }

    @Transactional
    public PublicMeetingCheckinReceiptVO confirmPublic(PublicMeetingCheckinConfirmRequest request,
                                                       String visitorSessionToken) {
        if (request == null) throw new BusinessException("会议签到参数不能为空");
        VisitorSessionService.VisitorSessionContext initial = requireCheckinSource(visitorSessionToken);
        String identityKey = checkinIdentityHash(initial);
        rateLimitService.check("public-site-meeting-checkin-confirm-identity", identityKey,
                10, Duration.ofMinutes(30));
        SiteMeetingCheckinQr qr = qrMapper.selectForUpdate(initial.effectiveSourceId());
        if (qr == null) throw BusinessException.notFound("会议签到入口不存在或已失效");
        SiteVisitInvitation invitation = requireMeetingInvitation(qr.getInvitationId(), true);
        LocalDateTime now = LocalDateTime.now();
        validatePublicCheckin(qr, invitation, now);
        ProjectInfo project = requireUsableProject(qr.getProjectId());
        VisitorSessionService.VisitorSessionContext context = sessionService.requireMeetingCheckin(
                visitorSessionToken, qr.getId(), qr.getProjectId());
        SiteMeetingVisitRegistration registration = findActiveRegistration(invitation.getId(), context, true);
        if (registration == null) throw stateConflict("未找到当前微信的预约登记，请重新扫码现场补录");

        Map<Long, PublicMeetingCheckinConfirmRequest.PersonSelection> selections = request.getAttendees().stream()
                .collect(Collectors.toMap(PublicMeetingCheckinConfirmRequest.PersonSelection::getPersonId,
                        Function.identity(), (first, second) -> first, LinkedHashMap::new));
        if (selections.size() != request.getAttendees().size()) throw new BusinessException("签到人员不能重复");
        List<SiteMeetingVisitPerson> people = lockOwnedPeople(registration, selections.keySet());
        LocationEvidence evidence = locationEvidence(request.getLocation(), project, qr);
        for (SiteMeetingVisitPerson person : people) {
            completeMissingName(person, selections.get(person.getId()).getCompletedName());
            checkInPerson(invitation, registration, person, qr, context, METHOD_VENUE_QR, now, evidence, null, null);
        }
        return receipt(registration, evidence, now);
    }

    @Transactional
    public PublicMeetingCheckinReceiptVO walkInPublic(PublicMeetingCheckinWalkInRequest request,
                                                      String visitorSessionToken) {
        if (request == null) throw new BusinessException("现场补录参数不能为空");
        VisitorSessionService.VisitorSessionContext initial = requireCheckinSource(visitorSessionToken);
        String identityKey = checkinIdentityHash(initial);
        rateLimitService.check("public-site-meeting-checkin-walkin-identity", identityKey,
                10, Duration.ofMinutes(30));
        SiteMeetingCheckinQr qr = qrMapper.selectForUpdate(initial.effectiveSourceId());
        if (qr == null) throw BusinessException.notFound("会议签到入口不存在或已失效");
        SiteVisitInvitation invitation = requireMeetingInvitation(qr.getInvitationId(), true);
        LocalDateTime now = LocalDateTime.now();
        validatePublicCheckin(qr, invitation, now);
        ProjectInfo project = requireProjectForUpdate(qr.getProjectId());
        ensureProjectActive(project);
        VisitorSessionService.VisitorSessionContext context = sessionService.requireMeetingCheckin(
                visitorSessionToken, qr.getId(), qr.getProjectId());
        SiteMeetingVisitRegistration existing = findActiveRegistration(invitation.getId(), context, true);
        if (existing != null) {
            if (SOURCE_WALK_IN.equals(sourceOf(existing))) return receipt(existing,
                    latestEvidence(existing.getId()), now);
            throw stateConflict("当前微信已有预约登记，请重新扫码后选择实际到场人员");
        }
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) throw new BusinessException("请阅读并同意隐私告知");
        VisitorSubmissionNormalizer.Submission submission = normalizeWalkIn(request);
        String registrationIdentity = registrationIdentityHash(context);
        SiteMeetingVisitRegistration registration;
        try {
            registration = createRegistration(invitation, submission, SOURCE_WALK_IN,
                    context.appId(), registrationIdentity, now, null);
        } catch (DuplicateKeyException duplicate) {
            SiteMeetingVisitRegistration raced = registrationMapper.selectActiveForUpdate(
                    invitation.getId(), context.appId(), registrationIdentity);
            if (raced != null && SOURCE_WALK_IN.equals(sourceOf(raced))) {
                return receipt(raced, latestEvidence(raced.getId()), now);
            }
            throw stateConflict("会议现场登记冲突，请重新扫码");
        }
        List<SiteMeetingVisitPerson> people = insertPeople(registration, submission);
        writeAudit(invitation, registration, null, qr, "REGISTER", null,
                null, registrationSnapshot(registration, people), "访客现场补录会议登记");
        LocationEvidence evidence = locationEvidence(request.getLocation(), project, qr);
        for (SiteMeetingVisitPerson person : people) {
            checkInPerson(invitation, registration, person, qr, context, METHOD_VENUE_QR, now, evidence, null, null);
        }
        personalProfiles.saveOnSubmission(context, null, submission);
        return receipt(registration, evidence, now);
    }

    public List<com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO> publicProfiles(String token) {
        VisitorSessionService.VisitorSessionContext context = requireCheckinContext(token);
        rateLimitService.check("public-site-meeting-checkin-profile-list-identity", checkinIdentityHash(context),
                60, Duration.ofMinutes(10));
        return profileService.publicList(context);
    }

    public com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO publicProfile(String token, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requireCheckinContext(token);
        rateLimitService.check("public-site-meeting-checkin-profile-detail-identity", checkinIdentityHash(context),
                60, Duration.ofMinutes(10));
        return profileService.publicDetail(context, profileCode);
    }

    public void disablePublicProfile(String token, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requireCheckinContext(token);
        rateLimitService.check("public-site-meeting-checkin-profile-disable-identity", checkinIdentityHash(context),
                10, Duration.ofMinutes(30));
        profileService.publicDisable(context, profileCode);
    }

    @Transactional
    public SiteMeetingAttendeeVO createManualWalkIn(Long invitationId, SiteMeetingWalkInCreateRequest request,
                                                    SysUser user) {
        if (request == null) throw new BusinessException("现场补录参数不能为空");
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, true);
        requirePermission(user, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) throw stateConflict("已作废会议不能补录签到");
        SiteMeetingCheckinQr qr = requireCurrentQr(invitation, user, true);
        LocalDateTime occurred = validateManualTime(request.getOccurredTime(), qr);
        VisitorSubmissionNormalizer.Submission submission = normalizeWalkIn(request);
        SiteMeetingVisitRegistration registration;
        try {
            registration = createRegistration(invitation, submission, SOURCE_WALK_IN,
                    null, null, LocalDateTime.now(), null);
        } catch (DuplicateKeyException duplicate) {
            throw stateConflict("会议现场登记编号冲突，请重试");
        }
        List<SiteMeetingVisitPerson> people = insertPeople(registration, submission);
        writeAudit(invitation, registration, null, qr, "REGISTER", user,
                null, registrationSnapshot(registration, people), requiredReason(request.getReason()));
        LocationEvidence evidence = LocationEvidence.manual();
        for (SiteMeetingVisitPerson person : people) {
            checkInPerson(invitation, registration, person, qr, null, METHOD_STAFF_MANUAL,
                    occurred, evidence, user, request.getReason());
        }
        recordOperation(user, "CREATE_MEETING_WALK_IN", registration.getId(), "后台现场补录并签到");
        return toAttendee(findAttendeeRow(people.get(0).getId()));
    }

    @Transactional
    public SiteMeetingAttendeeVO manualCheckIn(Long personId, SiteMeetingAttendanceActionRequest request,
                                               SysUser user) {
        if (request == null) throw new BusinessException("人工补签参数不能为空");
        SiteMeetingVisitPerson person = requirePersonForUpdate(personId);
        SiteMeetingVisitRegistration registration = requireRegistrationForUpdate(person.getRegistrationId());
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), true);
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) throw stateConflict("已作废会议不能补签");
        requireActiveRegistration(registration);
        SiteMeetingCheckinQr qr = requireCurrentQr(invitation, user, true);
        LocalDateTime occurred = validateManualTime(request.getOccurredTime(), qr);
        String reason = requiredReason(request.getReason());
        SiteMeetingAttendance existing = attendanceMapper.selectByPersonForUpdate(personId);
        if (existing != null) requireAttendanceVersion(existing, request.getVersion());
        else if (!Integer.valueOf(0).equals(request.getVersion())) throw stateConflict("人员签到状态已变化，请刷新后重试");
        if (!StringUtils.hasText(person.getPersonName())) throw new BusinessException("请先补全参会人员姓名");
        checkInPerson(invitation, registration, person, qr, null, METHOD_STAFF_MANUAL,
                occurred, LocationEvidence.manual(), user, reason);
        recordOperation(user, "MANUAL_MEETING_CHECKIN", personId, "人工补签会议人员");
        return toAttendee(findAttendeeRow(personId));
    }

    @Transactional
    public SiteMeetingAttendeeVO revoke(Long personId, SiteMeetingAttendanceActionRequest request, SysUser user) {
        if (request == null) throw new BusinessException("撤销签到参数不能为空");
        SiteMeetingVisitPerson person = requirePersonForUpdate(personId);
        SiteMeetingVisitRegistration registration = requireRegistrationForUpdate(person.getRegistrationId());
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), true);
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        requireActiveRegistration(registration);
        SiteMeetingAttendance attendance = attendanceMapper.selectByPersonForUpdate(personId);
        if (attendance == null || !ATTENDANCE_CHECKED_IN.equals(attendance.getStatus())) {
            throw stateConflict("该人员当前没有有效签到");
        }
        requireAttendanceVersion(attendance, request.getVersion());
        String reason = requiredReason(request.getReason());
        Map<String, Object> before = attendanceSnapshot(attendance);
        attendance.setStatus(ATTENDANCE_REVOKED);
        attendance.setRevokedById(user.getId());
        attendance.setRevokedByName(displayName(user));
        attendance.setRevokedTime(LocalDateTime.now());
        attendance.setRevokeReason(reason);
        attendance.setVersion(versionOf(attendance) + 1);
        attendance.setUpdateTime(LocalDateTime.now());
        requireSingle(attendanceMapper.updateById(attendance), "会议签到撤销");
        writeAudit(invitation, registration, person, null, "CHECKIN_REVOKE", user,
                before, attendanceSnapshot(attendance), reason);
        recordOperation(user, "REVOKE_MEETING_CHECKIN", personId, "撤销会议人员签到");
        return toAttendee(findAttendeeRow(personId));
    }

    @Transactional
    public SiteMeetingAttendeeVO updateAttendee(Long personId, SiteMeetingAttendeeUpdateRequest request,
                                                SysUser user) {
        if (request == null) throw new BusinessException("参会人员更正参数不能为空");
        SiteMeetingVisitPerson person = requirePersonForUpdate(personId);
        SiteMeetingVisitRegistration registration = requireRegistrationForUpdate(person.getRegistrationId());
        SiteVisitInvitation invitation = requireMeetingInvitation(registration.getInvitationId(), true);
        requirePermission(user, registration.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        SiteMeetingAttendance attendance = attendanceMapper.selectByPersonForUpdate(personId);
        if (attendance != null) requireAttendanceVersion(attendance, request.getVersion());
        else if (!Integer.valueOf(0).equals(request.getVersion())) throw stateConflict("人员信息已变化，请刷新后重试");
        String reason = requiredReason(request.getReason());
        Map<String, Object> before = personSnapshot(person);
        boolean contact = VisitorSubmissionNormalizer.PERSON_CONTACT.equals(person.getPersonType());
        String personName = requiredText(request.getPersonName(), 50, "姓名");
        String personCompany = contact
                ? requiredText(request.getPersonCompany(), 200, "单位")
                : optionalText(request.getPersonCompany(), 200, "单位");
        String phone = contact
                ? requiredText(request.getPersonPhone(), 11, "手机号码")
                : optionalText(request.getPersonPhone(), 11, "手机号码");
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) throw new BusinessException("手机号码格式不正确");
        person.setPersonName(personName);
        person.setPersonCompany(personCompany);
        person.setPhoneEncrypted(cryptoService.encrypt(phone));
        person.setUpdateTime(LocalDateTime.now());
        requireSingle(personMapper.updateById(person), "参会人员更正");
        if (contact) {
            registration.setVisitorCompany(personCompany);
            registration.setContactName(personName);
            registration.setContactPhoneEncrypted(cryptoService.encrypt(phone));
            registration.setVersion((registration.getVersion() == null ? 0 : registration.getVersion()) + 1);
            registration.setUpdateTime(LocalDateTime.now());
            requireSingle(registrationMapper.updateById(registration), "会议登记姓名同步");
        }
        if (attendance != null) {
            attendance.setVersion(versionOf(attendance) + 1);
            attendance.setUpdateTime(LocalDateTime.now());
            requireSingle(attendanceMapper.updateById(attendance), "参会人员更正版本更新");
        }
        writeAudit(invitation, registration, person, null, "ATTENDEE_UPDATE", user,
                before, personSnapshot(person), reason);
        recordOperation(user, "UPDATE_MEETING_ATTENDEE", personId, "更正会议参会人员信息");
        return toAttendee(findAttendeeRow(personId));
    }

    @Transactional
    public ExportFile export(Long projectId, Long invitationId, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.SITE_ACCESS_EXPORT);
        SiteVisitInvitation invitation = requireMeetingInvitation(invitationId, false);
        if (!Objects.equals(projectId, invitation.getProjectId())) throw BusinessException.of(403, "会议邀请不属于当前项目");
        List<Map<String, Object>> rows = attendanceMapper.selectExportRows(invitationId);
        if (rows.size() > MAX_EXPORT_ROWS) throw new BusinessException("导出人员超过50000人，请缩小范围");
        ProjectInfo project = requireProject(projectId);
        SiteMeetingAttendanceSummaryVO summary = summaryFrom(attendanceMapper.selectSummary(invitationId));
        SiteMeetingCheckinQr qr = qrMapper.selectCurrent(invitationId);
        byte[] content = buildWorkbook(project, invitation, qr, summary, rows, user);
        writeAudit(invitation, null, null, qr, "CHECKIN_EXPORT", user,
                null, Map.of("visitorRows", rows.size()), "导出会议签到名单，共" + rows.size() + "人");
        recordOperation(user, "EXPORT_MEETING_ATTENDANCE", invitationId, "导出会议签到名单，共" + rows.size() + "人");
        return new ExportFile("场内管理_会议签到_" + safeFileName(project.getShortName() == null
                ? project.getProjectName() : project.getShortName()) + "_" + safeFileName(invitation.getInviteNo()) + ".xlsx", content);
    }

    private SiteMeetingVisitRegistration createRegistration(SiteVisitInvitation invitation,
                                                              VisitorSubmissionNormalizer.Submission submission,
                                                              String source, String appId, String identityHash,
                                                              LocalDateTime registeredTime, Long sourceProfileId) {
        SiteMeetingVisitRegistration registration = new SiteMeetingVisitRegistration();
        registration.setRegistrationNo("MVR-" + DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(registeredTime)
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        registration.setInvitationId(invitation.getId());
        registration.setProjectId(invitation.getProjectId());
        registration.setWechatAppId(appId);
        registration.setVisitorIdentityHash(identityHash);
        registration.setStatus(MeetingVisitService.STATUS_REGISTERED);
        registration.setRegistrationSource(source);
        registration.setVisitorCompany(submission.visitorCompany());
        registration.setContactName(submission.contactName());
        registration.setContactPhoneEncrypted(cryptoService.encrypt(submission.contactPhone()));
        registration.setVisitorCount(submission.people().size());
        registration.setTravelMode(submission.travelMode());
        registration.setVehiclePlate(submission.vehiclePlate());
        registration.setVisitorRemark(submission.visitorRemark());
        registration.setSourceProfileId(sourceProfileId);
        registration.setPrivacyAgreedTime(LocalDateTime.now());
        registration.setRegisteredTime(registeredTime);
        registration.setVersion(0);
        registration.setDeleted(0);
        registration.setCreateTime(LocalDateTime.now());
        registration.setUpdateTime(LocalDateTime.now());
        requireSingle(registrationMapper.insert(registration), "会议现场登记");
        return registration;
    }

    private List<SiteMeetingVisitPerson> insertPeople(SiteMeetingVisitRegistration registration,
                                                       VisitorSubmissionNormalizer.Submission submission) {
        List<SiteMeetingVisitPerson> result = new ArrayList<>();
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
            requireSingle(personMapper.insert(person), "会议现场人员写入");
            result.add(person);
        }
        return result;
    }

    private void checkInPerson(SiteVisitInvitation invitation, SiteMeetingVisitRegistration registration,
                               SiteMeetingVisitPerson person, SiteMeetingCheckinQr qr,
                               VisitorSessionService.VisitorSessionContext context, String method,
                               LocalDateTime checkinTime, LocationEvidence evidence,
                               SysUser operator, String comment) {
        SiteMeetingAttendance attendance = attendanceMapper.selectByPersonForUpdate(person.getId());
        if (attendance != null && ATTENDANCE_CHECKED_IN.equals(attendance.getStatus())) return;
        Map<String, Object> before = attendance == null ? null : attendanceSnapshot(attendance);
        if (attendance == null) {
            attendance = new SiteMeetingAttendance();
            attendance.setInvitationId(invitation.getId());
            attendance.setRegistrationId(registration.getId());
            attendance.setPersonId(person.getId());
            attendance.setProjectId(registration.getProjectId());
            attendance.setVersion(0);
            attendance.setDeleted(0);
            attendance.setCreateTime(LocalDateTime.now());
        } else {
            attendance.setVersion(versionOf(attendance) + 1);
        }
        attendance.setCheckinQrId(qr == null ? null : qr.getId());
        attendance.setStatus(ATTENDANCE_CHECKED_IN);
        attendance.setCheckinMethod(method);
        attendance.setCheckinTime(checkinTime);
        attendance.setWechatAppId(context == null ? null : context.appId());
        attendance.setCheckinIdentityHash(context == null ? null : checkinIdentityHash(context));
        attendance.setLocationResult(evidence.result());
        attendance.setDistanceMeters(evidence.distanceMeters());
        attendance.setAccuracyMeters(evidence.accuracyMeters());
        attendance.setReferenceProjectVersion(evidence.projectVersion());
        attendance.setRevokedById(null);
        attendance.setRevokedByName(null);
        attendance.setRevokedTime(null);
        attendance.setRevokeReason(null);
        attendance.setUpdateTime(LocalDateTime.now());
        if (attendance.getId() == null) requireSingle(attendanceMapper.insert(attendance), "会议人员签到");
        else requireSingle(attendanceMapper.updateById(attendance), "会议人员重新签到");
        String action = SOURCE_WALK_IN.equals(sourceOf(registration)) ? "WALKIN_CHECKIN"
                : METHOD_STAFF_MANUAL.equals(method) ? "MANUAL_CHECKIN" : "SELF_CHECKIN";
        writeAudit(invitation, registration, person, qr, action, operator,
                before, attendanceSnapshot(attendance), comment == null ? "逐人完成会议签到" : comment);
    }

    private PublicMeetingCheckinReceiptVO receipt(SiteMeetingVisitRegistration registration,
                                                   LocationEvidence evidence, LocalDateTime now) {
        List<PublicMeetingCheckinAttendeeVO> people = publicAttendees(registration.getId());
        int checked = (int) people.stream().filter(item -> ATTENDANCE_CHECKED_IN.equals(item.getAttendanceStatus())).count();
        PublicMeetingCheckinReceiptVO vo = new PublicMeetingCheckinReceiptVO();
        vo.setRegistrationNo(registration.getRegistrationNo());
        vo.setRegistrationSource(sourceOf(registration));
        vo.setCheckedInCount(checked);
        vo.setPendingCount(Math.max(0, people.size() - checked));
        vo.setLocationResult(evidence == null ? null : evidence.result());
        vo.setDistanceMeters(evidence == null ? null : evidence.distanceMeters());
        vo.setAccuracyMeters(evidence == null ? null : evidence.accuracyMeters());
        vo.setServerTime(now);
        vo.setAttendees(people);
        return vo;
    }

    private List<PublicMeetingCheckinAttendeeVO> publicAttendees(Long registrationId) {
        List<SiteMeetingVisitPerson> people = personMapper.selectList(new LambdaQueryWrapper<SiteMeetingVisitPerson>()
                .eq(SiteMeetingVisitPerson::getRegistrationId, registrationId)
                .orderByAsc(SiteMeetingVisitPerson::getSortOrder).orderByAsc(SiteMeetingVisitPerson::getId));
        if (people.isEmpty()) return List.of();
        Map<Long, SiteMeetingAttendance> attendance = attendanceMapper.selectList(
                new LambdaQueryWrapper<SiteMeetingAttendance>().in(SiteMeetingAttendance::getPersonId,
                        people.stream().map(SiteMeetingVisitPerson::getId).toList())).stream()
                .collect(Collectors.toMap(SiteMeetingAttendance::getPersonId, Function.identity()));
        return people.stream().map(person -> {
            SiteMeetingAttendance state = attendance.get(person.getId());
            PublicMeetingCheckinAttendeeVO vo = new PublicMeetingCheckinAttendeeVO();
            vo.setPersonId(person.getId());
            vo.setPersonType(person.getPersonType());
            vo.setPersonCompany(person.getPersonCompany());
            vo.setPersonName(person.getPersonName());
            vo.setSortOrder(person.getSortOrder());
            vo.setAttendanceStatus(state == null ? "PENDING" : state.getStatus());
            vo.setCheckinTime(state == null ? null : state.getCheckinTime());
            return vo;
        }).toList();
    }

    private List<SiteMeetingVisitPerson> lockOwnedPeople(SiteMeetingVisitRegistration registration, Set<Long> ids) {
        List<Long> sorted = ids.stream().sorted().toList();
        List<SiteMeetingVisitPerson> result = new ArrayList<>();
        for (Long id : sorted) {
            SiteMeetingVisitPerson person = personMapper.selectForUpdate(id);
            if (person == null || !Objects.equals(person.getRegistrationId(), registration.getId())) {
                throw BusinessException.of(403, "签到人员不属于当前微信登记组");
            }
            result.add(person);
        }
        return result;
    }

    private void completeMissingName(SiteMeetingVisitPerson person, String completedName) {
        if (StringUtils.hasText(person.getPersonName())) {
            if (StringUtils.hasText(completedName) && !person.getPersonName().trim().equals(completedName.trim())) {
                throw new BusinessException("已有姓名不能在访客签到时修改，请联系工作人员更正");
            }
            return;
        }
        person.setPersonName(requiredText(completedName, 50, "到场人员姓名"));
        person.setUpdateTime(LocalDateTime.now());
        requireSingle(personMapper.updateById(person), "补全到场人员姓名");
    }

    private VisitorSubmissionNormalizer.Submission normalizeWalkIn(PublicMeetingCheckinWalkInRequest request) {
        List<SiteVisitPersonRequest> companions = request.getCompanions().stream().map(value -> {
            SiteVisitPersonRequest person = new SiteVisitPersonRequest();
            person.setPersonCompany(value.getPersonCompany());
            person.setPersonName(requiredText(value.getPersonName(), 50, "同行人员姓名"));
            person.setPersonPhone(value.getPersonPhone());
            return person;
        }).toList();
        return VisitorSubmissionNormalizer.normalize(request.getVisitorCompany(), request.getContactName(),
                request.getContactPhone(), companions, request.getTravelMode(), request.getVehiclePlate(),
                request.getVisitorRemark());
    }

    private VisitorSubmissionNormalizer.Submission normalizeWalkIn(SiteMeetingWalkInCreateRequest request) {
        List<SiteVisitPersonRequest> companions = request.getCompanions().stream().map(value -> {
            SiteVisitPersonRequest person = new SiteVisitPersonRequest();
            person.setPersonCompany(value.getPersonCompany());
            person.setPersonName(requiredText(value.getPersonName(), 50, "同行人员姓名"));
            person.setPersonPhone(value.getPersonPhone());
            return person;
        }).toList();
        return VisitorSubmissionNormalizer.normalize(request.getVisitorCompany(), request.getContactName(),
                request.getContactPhone(), companions, request.getTravelMode(), request.getVehiclePlate(),
                request.getVisitorRemark());
    }

    private LocationEvidence locationEvidence(MeetingCheckinLocationRequest request, ProjectInfo project,
                                               SiteMeetingCheckinQr qr) {
        int accuracy = request.getAccuracyMeters() == null ? 0 : request.getAccuracyMeters();
        int projectVersion = project.getProfileVersion() == null ? 0 : project.getProfileVersion();
        if (!Boolean.TRUE.equals(request.getLocationAvailable())
                || request.getLatitude() == null || request.getLongitude() == null) {
            return new LocationEvidence(LOCATION_UNAVAILABLE, null, accuracy, projectVersion);
        }
        var reference = ProjectCoordinateConverter.toGcj02(
                project.getLongitude(), project.getLatitude(), project.getCoordinateType());
        if (reference.isEmpty()) return new LocationEvidence(LOCATION_NO_REFERENCE, null, accuracy, projectVersion);
        int distance = (int) Math.round(haversineMeters(request.getLatitude().doubleValue(),
                request.getLongitude().doubleValue(), reference.get().latitude().doubleValue(),
                reference.get().longitude().doubleValue()));
        return new LocationEvidence(distance <= qr.getLocationRadiusMeters() ? LOCATION_IN_RANGE : LOCATION_OUT_OF_RANGE,
                distance, accuracy, projectVersion);
    }

    private double haversineMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double radius = 6_371_000d;
        double lat = Math.toRadians(latitude2 - latitude1);
        double lon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private VisitorSessionService.VisitorSessionContext requireCheckinSource(String token) {
        VisitorSessionService.VisitorSessionContext context = sessionService.require(token);
        if (!VisitorSessionService.SOURCE_MEETING_CHECKIN_QR.equals(context.effectiveSourceType())) {
            throw BusinessException.of(403, "当前访客会话不能用于会议签到");
        }
        return context;
    }

    private VisitorSessionService.VisitorSessionContext requireCheckinContext(String token) {
        VisitorSessionService.VisitorSessionContext initial = requireCheckinSource(token);
        SiteMeetingCheckinQr qr = qrMapper.selectById(initial.effectiveSourceId());
        if (qr == null) throw BusinessException.notFound("会议签到入口不存在或已失效");
        SiteVisitInvitation invitation = requireMeetingInvitation(qr.getInvitationId(), false);
        validatePublicCheckin(qr, invitation, LocalDateTime.now());
        requireUsableProject(qr.getProjectId());
        return sessionService.requireMeetingCheckin(token, qr.getId(), qr.getProjectId());
    }

    private SiteMeetingVisitRegistration findActiveRegistration(Long invitationId,
                                                                 VisitorSessionService.VisitorSessionContext context,
                                                                 boolean lock) {
        String hash = registrationIdentityHash(context);
        if (lock) return registrationMapper.selectActiveForUpdate(invitationId, context.appId(), hash);
        return registrationMapper.selectOne(new LambdaQueryWrapper<SiteMeetingVisitRegistration>()
                .eq(SiteMeetingVisitRegistration::getInvitationId, invitationId)
                .eq(SiteMeetingVisitRegistration::getWechatAppId, context.appId())
                .eq(SiteMeetingVisitRegistration::getVisitorIdentityHash, hash)
                .eq(SiteMeetingVisitRegistration::getStatus, MeetingVisitService.STATUS_REGISTERED)
                .orderByDesc(SiteMeetingVisitRegistration::getRegisteredTime)
                .orderByDesc(SiteMeetingVisitRegistration::getId).last("LIMIT 1"));
    }

    private String registrationIdentityHash(VisitorSessionService.VisitorSessionContext context) {
        return cryptoService.fingerprint("site-access:meeting-registration:v1",
                context.appId() + ":" + sessionService.decryptOpenid(context));
    }

    private String checkinIdentityHash(VisitorSessionService.VisitorSessionContext context) {
        return cryptoService.fingerprint("site-access:meeting-checkin:v1",
                context.appId() + ":" + sessionService.decryptOpenid(context));
    }

    private SiteMeetingCheckinQr resolveQr(String rawScene) {
        String scene = requiredText(rawScene, 64, "会议签到码");
        if (scene.startsWith("MC:")) scene = scene.substring(3);
        if (!scene.matches("^[A-Za-z0-9_-]{20,32}$")) throw BusinessException.notFound("会议签到入口不存在或已失效");
        SiteMeetingCheckinQr qr = qrMapper.selectOne(new LambdaQueryWrapper<SiteMeetingCheckinQr>()
                .eq(SiteMeetingCheckinQr::getSceneTokenHash, cryptoService.digest(scene)).last("LIMIT 1"));
        if (qr == null) throw BusinessException.notFound("会议签到入口不存在或已失效");
        return qr;
    }

    private void validatePublicCheckin(SiteMeetingCheckinQr qr, SiteVisitInvitation invitation, LocalDateTime now) {
        if (QR_ROTATED.equals(qr.getQrStatus())) throw BusinessException.of(410, "会议签到码已轮换");
        if (QR_DISABLED.equals(qr.getQrStatus())) throw BusinessException.of(410, "会议签到入口已停用");
        if (!QR_ENABLED.equals(qr.getQrStatus())) throw BusinessException.notFound("会议签到入口不存在");
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) throw BusinessException.of(410, "会议邀请已作废");
        if (invitation.getVisitEndTime() == null || !now.isBefore(invitation.getVisitEndTime())) {
            throw BusinessException.of(410, "会议已经结束");
        }
        if (now.isBefore(qr.getCheckinStartTime())) throw stateConflict("会议签到尚未开始");
        if (!now.isBefore(qr.getCheckinEndTime())) throw BusinessException.of(410, "会议签到已经结束");
    }

    private SiteMeetingCheckinQr requireCurrentQr(SiteVisitInvitation invitation, SysUser user, boolean lock) {
        SiteMeetingCheckinQr qr = lock ? qrMapper.selectCurrentForUpdate(invitation.getId())
                : qrMapper.selectCurrent(invitation.getId());
        return qr == null ? provisioner.provision(invitation, user) : qr;
    }

    private void ensureMutableMeeting(SiteVisitInvitation invitation) {
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) throw stateConflict("已作废会议不能管理签到码");
        if (invitation.getVisitEndTime() == null || !invitation.getVisitEndTime().isAfter(LocalDateTime.now())) {
            throw stateConflict("已结束会议不能修改签到码");
        }
    }

    private SiteVisitInvitation requireMeetingInvitation(Long id, boolean lock) {
        SiteVisitInvitation invitation = id == null ? null
                : lock ? invitationMapper.selectForUpdate(id) : invitationMapper.selectById(id);
        if (invitation == null || !SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) {
            throw BusinessException.notFound("会议邀请不存在");
        }
        return invitation;
    }

    private SiteMeetingVisitRegistration requireRegistrationForUpdate(Long id) {
        SiteMeetingVisitRegistration registration = registrationMapper.selectForUpdate(id);
        if (registration == null) throw BusinessException.notFound("会议登记组不存在");
        return registration;
    }

    private void requireActiveRegistration(SiteMeetingVisitRegistration registration) {
        if (!MeetingVisitService.STATUS_REGISTERED.equals(registration.getStatus())) {
            throw stateConflict("已作废登记不能补签或更正");
        }
    }

    private SiteMeetingVisitPerson requirePersonForUpdate(Long id) {
        SiteMeetingVisitPerson person = personMapper.selectForUpdate(id);
        if (person == null) throw BusinessException.notFound("会议参会人员不存在");
        return person;
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
            throw BusinessException.of(410, "项目已停用，会议签到入口不可用");
        }
    }

    private void validateWindow(SiteVisitInvitation invitation, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) throw new BusinessException("签到开始时间必须早于结束时间");
        if (start.isBefore(invitation.getVisitStartTime().minusDays(1))) throw new BusinessException("签到最多提前24小时开放");
        if (end.isAfter(invitation.getVisitEndTime())) throw new BusinessException("签到结束时间不能晚于会议结束时间");
    }

    private LocalDateTime validateManualTime(LocalDateTime requested, SiteMeetingCheckinQr qr) {
        LocalDateTime value = requested == null ? LocalDateTime.now() : requested;
        if (value.isAfter(LocalDateTime.now())) throw new BusinessException("人工签到时间不能晚于当前时间");
        if (value.isBefore(qr.getCheckinStartTime()) || value.isAfter(qr.getCheckinEndTime())) {
            throw new BusinessException("人工签到时间必须位于本场签到窗口内");
        }
        return value;
    }

    private SiteMeetingCheckinSettingsVO toSettings(SiteMeetingCheckinQr qr, SiteVisitInvitation invitation,
                                                      ProjectInfo project, LocalDateTime now) {
        SiteMeetingCheckinSettingsVO vo = new SiteMeetingCheckinSettingsVO();
        vo.setId(qr.getId());
        vo.setInvitationId(qr.getInvitationId());
        vo.setProjectId(qr.getProjectId());
        vo.setQrStatus(qr.getQrStatus());
        vo.setEffectiveStatus(effectiveQrStatus(qr, invitation, now));
        vo.setQrVersion(qr.getQrVersion());
        vo.setCheckinStartTime(qr.getCheckinStartTime());
        vo.setCheckinEndTime(qr.getCheckinEndTime());
        vo.setLocationRadiusMeters(qr.getLocationRadiusMeters());
        vo.setProjectLocationAvailable(ProjectCoordinateConverter.toGcj02(
                project.getLongitude(), project.getLatitude(), project.getCoordinateType()).isPresent());
        vo.setVersion(qr.getVersion());
        vo.setServerTime(now);
        vo.setCreateTime(qr.getCreateTime());
        vo.setUpdateTime(qr.getUpdateTime());
        return vo;
    }

    private String effectiveQrStatus(SiteMeetingCheckinQr qr, SiteVisitInvitation invitation, LocalDateTime now) {
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) return "VOIDED";
        if (!QR_ENABLED.equals(qr.getQrStatus())) return qr.getQrStatus();
        if (now.isBefore(qr.getCheckinStartTime())) return "NOT_STARTED";
        if (!now.isBefore(qr.getCheckinEndTime())) return "ENDED";
        return "OPEN";
    }

    private PublicMeetingCheckinMeetingVO toPublicMeeting(SiteVisitInvitation invitation, SiteMeetingCheckinQr qr,
                                                           ProjectInfo project, LocalDateTime now) {
        PublicMeetingCheckinMeetingVO vo = new PublicMeetingCheckinMeetingVO();
        vo.setInviteNo(invitation.getInviteNo());
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setPurpose(invitation.getPurpose());
        vo.setVisitLocation(invitation.getVisitLocation());
        vo.setHostName(invitation.getHostName());
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setVisitEndTime(invitation.getVisitEndTime());
        vo.setCheckinStartTime(qr.getCheckinStartTime());
        vo.setCheckinEndTime(qr.getCheckinEndTime());
        vo.setLocationRadiusMeters(qr.getLocationRadiusMeters());
        vo.setProjectLocationAvailable(ProjectCoordinateConverter.toGcj02(
                project.getLongitude(), project.getLatitude(), project.getCoordinateType()).isPresent());
        vo.setServerTime(now);
        return vo;
    }

    private SiteMeetingAttendanceSummaryVO summaryFrom(Map<String, Object> values) {
        SiteMeetingAttendanceSummaryVO vo = new SiteMeetingAttendanceSummaryVO();
        long reserved = longValue(values, "reservedPersonCount");
        long reservedChecked = longValue(values, "reservedCheckedInCount");
        vo.setReservedPersonCount(reserved);
        vo.setReservedCheckedInCount(reservedChecked);
        vo.setReservedPendingCount(Math.max(0, reserved - reservedChecked));
        vo.setWalkInCheckedInCount(longValue(values, "walkInCheckedInCount"));
        vo.setTotalCheckedInCount(longValue(values, "totalCheckedInCount"));
        vo.setReservedAttendanceRate(reserved == 0 ? 0d : Math.round(reservedChecked * 10000d / reserved) / 100d);
        vo.setInRangeCount(longValue(values, "inRangeCount"));
        vo.setOutOfRangeCount(longValue(values, "outOfRangeCount"));
        vo.setUnavailableLocationCount(longValue(values, "unavailableLocationCount"));
        vo.setNoReferenceLocationCount(longValue(values, "noReferenceLocationCount"));
        vo.setManualLocationCount(longValue(values, "manualLocationCount"));
        return vo;
    }

    private SiteMeetingAttendeeVO toAttendee(Map<String, Object> row) {
        SiteMeetingAttendeeVO vo = new SiteMeetingAttendeeVO();
        vo.setPersonId(longObject(row, "personId"));
        vo.setRegistrationId(longObject(row, "registrationId"));
        vo.setRegistrationNo(text(row.get("registrationNo")));
        vo.setRegistrationSource(text(row.get("registrationSource")));
        vo.setRegistrationStatus(text(row.get("registrationStatus")));
        vo.setPersonType(text(row.get("personType")));
        vo.setPersonCompany(textOrNull(row.get("personCompany")));
        vo.setPersonName(textOrNull(row.get("personName")));
        vo.setPersonPhone(cryptoService.decrypt(textOrNull(row.get("phoneEncrypted"))));
        vo.setSortOrder(intObject(row, "sortOrder"));
        vo.setRegisteredTime(dateTime(row.get("registeredTime")));
        vo.setTravelMode(textOrNull(row.get("travelMode")));
        vo.setVehiclePlate(textOrNull(row.get("vehiclePlate")));
        vo.setAttendanceStatus(StringUtils.hasText(textOrNull(row.get("attendanceStatus")))
                ? text(row.get("attendanceStatus")) : "PENDING");
        vo.setCheckinMethod(textOrNull(row.get("checkinMethod")));
        vo.setCheckinTime(dateTime(row.get("checkinTime")));
        vo.setLocationResult(textOrNull(row.get("locationResult")));
        vo.setDistanceMeters(intObject(row, "distanceMeters"));
        vo.setAccuracyMeters(intObject(row, "accuracyMeters"));
        vo.setRevokedByName(textOrNull(row.get("revokedByName")));
        vo.setRevokedTime(dateTime(row.get("revokedTime")));
        vo.setRevokeReason(textOrNull(row.get("revokeReason")));
        vo.setVersion(intObject(row, "version") == null ? 0 : intObject(row, "version"));
        return vo;
    }

    private Map<String, Object> findAttendeeRow(Long personId) {
        Map<String, Object> row = attendanceMapper.selectAttendeeByPersonId(personId);
        if (row == null) throw BusinessException.notFound("会议参会人员不存在");
        return row;
    }

    private SiteMeetingVisitRegistration requireRegistrationForPerson(Long personId) {
        SiteMeetingVisitPerson person = personMapper.selectById(personId);
        if (person == null) throw BusinessException.notFound("会议参会人员不存在");
        SiteMeetingVisitRegistration registration = registrationMapper.selectById(person.getRegistrationId());
        if (registration == null) throw BusinessException.notFound("会议登记组不存在");
        return registration;
    }

    private LocationEvidence latestEvidence(Long registrationId) {
        SiteMeetingAttendance value = attendanceMapper.selectOne(new LambdaQueryWrapper<SiteMeetingAttendance>()
                .eq(SiteMeetingAttendance::getRegistrationId, registrationId)
                .eq(SiteMeetingAttendance::getStatus, ATTENDANCE_CHECKED_IN)
                .orderByDesc(SiteMeetingAttendance::getCheckinTime).last("LIMIT 1"));
        return value == null ? null : new LocationEvidence(value.getLocationResult(), value.getDistanceMeters(),
                value.getAccuracyMeters(), value.getReferenceProjectVersion());
    }

    private void writeAudit(SiteVisitInvitation invitation, SiteMeetingVisitRegistration registration,
                            SiteMeetingVisitPerson person, SiteMeetingCheckinQr qr, String action, SysUser operator,
                            Map<String, Object> before, Map<String, Object> after, String comment) {
        SiteMeetingVisitAuditLog audit = new SiteMeetingVisitAuditLog();
        audit.setRegistrationId(registration == null ? null : registration.getId());
        audit.setPersonId(person == null ? null : person.getId());
        audit.setCheckinQrId(qr == null ? null : qr.getId());
        audit.setInvitationId(invitation.getId());
        audit.setProjectId(invitation.getProjectId());
        audit.setActionType(action);
        audit.setOperatorId(operator == null ? null : operator.getId());
        audit.setOperatorName(operator == null ? "外访人员" : displayName(operator));
        audit.setBeforeSnapshotEncrypted(encrypt(before));
        audit.setAfterSnapshotEncrypted(encrypt(after));
        audit.setComment(optionalText(comment, 500, "审计说明"));
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "会议签到审计写入");
    }

    private String encrypt(Map<String, Object> value) {
        if (value == null) return null;
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会议签到审计快照生成失败", exception);
        }
    }

    private Map<String, Object> qrSnapshot(SiteMeetingCheckinQr qr) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qrStatus", qr.getQrStatus());
        result.put("qrVersion", qr.getQrVersion());
        result.put("checkinStartTime", qr.getCheckinStartTime());
        result.put("checkinEndTime", qr.getCheckinEndTime());
        result.put("locationRadiusMeters", qr.getLocationRadiusMeters());
        result.put("version", qr.getVersion());
        return result;
    }

    private Map<String, Object> registrationSnapshot(SiteMeetingVisitRegistration registration,
                                                      List<SiteMeetingVisitPerson> people) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registrationNo", registration.getRegistrationNo());
        result.put("registrationSource", sourceOf(registration));
        result.put("visitorCompany", registration.getVisitorCompany());
        result.put("contactName", registration.getContactName());
        result.put("visitorCount", registration.getVisitorCount());
        result.put("people", people.stream().map(this::personSnapshot).toList());
        return result;
    }

    private Map<String, Object> personSnapshot(SiteMeetingVisitPerson person) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personType", person.getPersonType());
        result.put("personCompany", person.getPersonCompany());
        result.put("personName", person.getPersonName());
        result.put("personPhone", cryptoService.decrypt(person.getPhoneEncrypted()));
        result.put("sortOrder", person.getSortOrder());
        return result;
    }

    private Map<String, Object> attendanceSnapshot(SiteMeetingAttendance attendance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", attendance.getStatus());
        result.put("checkinMethod", attendance.getCheckinMethod());
        result.put("checkinTime", attendance.getCheckinTime());
        result.put("locationResult", attendance.getLocationResult());
        result.put("distanceMeters", attendance.getDistanceMeters());
        result.put("accuracyMeters", attendance.getAccuracyMeters());
        result.put("revokedByName", attendance.getRevokedByName());
        result.put("revokedTime", attendance.getRevokedTime());
        result.put("revokeReason", attendance.getRevokeReason());
        result.put("version", attendance.getVersion());
        return result;
    }

    private byte[] buildWorkbook(ProjectInfo project, SiteVisitInvitation invitation, SiteMeetingCheckinQr qr,
                                 SiteMeetingAttendanceSummaryVO summary, List<Map<String, Object>> rows, SysUser user) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            Sheet summarySheet = workbook.createSheet("签到汇总");
            String[][] summaryRows = {
                    {"项目", text(project.getProjectName())}, {"会议邀请编号", text(invitation.getInviteNo())},
                    {"会议主题", text(invitation.getPurpose())}, {"会议地点", text(invitation.getVisitLocation())},
                    {"会议时间", format(invitation.getVisitStartTime()) + " 至 " + format(invitation.getVisitEndTime())},
                    {"签到窗口", qr == null ? "未配置" : format(qr.getCheckinStartTime()) + " 至 " + format(qr.getCheckinEndTime())},
                    {"二维码状态", qr == null ? "未配置" : text(qr.getQrStatus())},
                    {"预约人数", String.valueOf(summary.getReservedPersonCount())},
                    {"预约已签到", String.valueOf(summary.getReservedCheckedInCount())},
                    {"预约未签到", String.valueOf(summary.getReservedPendingCount())},
                    {"现场补录", String.valueOf(summary.getWalkInCheckedInCount())},
                    {"总到场人数", String.valueOf(summary.getTotalCheckedInCount())},
                    {"预约签到率", summary.getReservedAttendanceRate() + "%"},
                    {"定位范围内", String.valueOf(summary.getInRangeCount())},
                    {"定位超距", String.valueOf(summary.getOutOfRangeCount())},
                    {"定位不可用", String.valueOf(summary.getUnavailableLocationCount())},
                    {"项目无定位", String.valueOf(summary.getNoReferenceLocationCount())},
                    {"工作人员签到", String.valueOf(summary.getManualLocationCount())},
                    {"导出时间", format(LocalDateTime.now())}, {"导出人", displayName(user)}
            };
            for (int i = 0; i < summaryRows.length; i++) {
                Row row = summarySheet.createRow(i);
                row.createCell(0).setCellValue(summaryRows[i][0]);
                row.getCell(0).setCellStyle(headerStyle);
                row.createCell(1).setCellValue(safeExcelText(summaryRows[i][1]));
            }
            summarySheet.setColumnWidth(0, 20 * 256);
            summarySheet.setColumnWidth(1, 48 * 256);

            String[] headers = {"项目", "会议邀请编号", "会议主题", "登记来源", "登记编号", "单位", "人员类型",
                    "姓名", "手机号码", "预约时间", "签到状态", "签到时间", "签到方式", "定位结果", "距离(米)",
                    "精度(米)", "出行方式", "车牌号", "登记状态"};
            Sheet detail = workbook.createSheet("参会人员明细");
            detail.createFreezePane(0, 1);
            detail.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
            Row header = detail.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }
            int rowNumber = 1;
            for (Map<String, Object> value : rows) {
                SiteMeetingAttendeeVO attendee = toAttendee(value);
                Row row = detail.createRow(rowNumber++);
                List<String> cells = List.of(text(project.getProjectName()), text(invitation.getInviteNo()),
                        text(invitation.getPurpose()), SOURCE_WALK_IN.equals(attendee.getRegistrationSource()) ? "现场补录" : "预约",
                        text(attendee.getRegistrationNo()), text(attendee.getPersonCompany()),
                        VisitorSubmissionNormalizer.PERSON_CONTACT.equals(attendee.getPersonType()) ? "本人" : "同行人员",
                        text(attendee.getPersonName()), text(attendee.getPersonPhone()), format(attendee.getRegisteredTime()),
                        attendanceLabel(attendee.getAttendanceStatus()), format(attendee.getCheckinTime()),
                        methodLabel(attendee.getCheckinMethod()), locationLabel(attendee.getLocationResult()),
                        attendee.getDistanceMeters() == null ? "" : attendee.getDistanceMeters().toString(),
                        attendee.getAccuracyMeters() == null ? "" : attendee.getAccuracyMeters().toString(),
                        VisitorSubmissionNormalizer.TRAVEL_DRIVING.equals(attendee.getTravelMode()) ? "驾车" : "非驾车",
                        text(attendee.getVehiclePlate()), MeetingVisitService.STATUS_REGISTERED.equals(attendee.getRegistrationStatus()) ? "有效" : "已作废");
                for (int i = 0; i < cells.size(); i++) row.createCell(i).setCellValue(safeExcelText(cells.get(i)));
            }
            int[] widths = {24, 22, 28, 12, 22, 24, 12, 14, 18, 18, 12, 18, 14, 14, 12, 12, 12, 16, 12};
            for (int i = 0; i < widths.length; i++) detail.setColumnWidth(i, widths[i] * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("会议签到 Excel 生成失败");
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String attendanceLabel(String value) {
        if (ATTENDANCE_CHECKED_IN.equals(value)) return "已签到";
        if (ATTENDANCE_REVOKED.equals(value)) return "已撤销";
        return "未签到";
    }

    private String methodLabel(String value) {
        if (METHOD_VENUE_QR.equals(value)) return "会场扫码";
        if (METHOD_STAFF_MANUAL.equals(value)) return "后台补签";
        return "";
    }

    private String locationLabel(String value) {
        return switch (Objects.toString(value, "")) {
            case LOCATION_IN_RANGE -> "范围内";
            case LOCATION_OUT_OF_RANGE -> "超出范围";
            case LOCATION_UNAVAILABLE -> "定位不可用";
            case LOCATION_NO_REFERENCE -> "项目无定位";
            case LOCATION_MANUAL -> "人工处理";
            default -> "";
        };
    }

    private void touchQr(SiteMeetingCheckinQr qr, SysUser user) {
        qr.setUpdatedById(user.getId());
        qr.setUpdatedByName(displayName(user));
        qr.setVersion(versionOf(qr) + 1);
        qr.setUpdateTime(LocalDateTime.now());
    }

    private void requireVersion(SiteMeetingCheckinQr qr, Integer expected) {
        if (expected == null || !Objects.equals(versionOf(qr), expected)) throw stateConflict("会议签到设置已更新，请刷新后重试");
    }

    private void requireAttendanceVersion(SiteMeetingAttendance attendance, Integer expected) {
        if (expected == null || !Objects.equals(versionOf(attendance), expected)) throw stateConflict("人员签到状态已更新，请刷新后重试");
    }

    private int versionOf(SiteMeetingCheckinQr qr) { return qr.getVersion() == null ? 0 : qr.getVersion(); }
    private int versionOf(SiteMeetingAttendance attendance) { return attendance.getVersion() == null ? 0 : attendance.getVersion(); }

    private void requirePermission(SysUser user, Long projectId, String permission) {
        permissionService.requireSystemPermission(user.getId(), projectId, permission);
    }

    private void recordOperation(SysUser user, String action, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(action);
        log.setOperationDesc(description);
        log.setBusinessType("SITE_ACCESS_MEETING_CHECKIN");
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        requireSingle(operationLogMapper.insert(log), "系统操作日志写入");
    }

    private String sourceOf(SiteMeetingVisitRegistration registration) {
        return StringUtils.hasText(registration.getRegistrationSource()) ? registration.getRegistrationSource() : SOURCE_INVITATION;
    }

    private String normalizeOptional(String value, Set<String> allowed, String field) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(result)) throw new BusinessException(field + "不正确");
        return result;
    }

    private String requiredReason(String value) { return requiredText(value, 300, "操作原因"); }
    private String requiredText(String value, int max, String field) { return VisitorSubmissionNormalizer.requiredText(value, max, field); }
    private String optionalText(String value, int max, String field) { return VisitorSubmissionNormalizer.optionalText(value, max, field); }

    private long longValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long longObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String textOrNull(Object value) { return value == null ? null : String.valueOf(value); }
    private String format(LocalDateTime value) { return value == null ? "" : DISPLAY_TIME.format(value); }
    private String safeExcelText(String value) {
        String normalized = text(value);
        String stripped = normalized.stripLeading();
        return !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0 ? "'" + normalized : normalized;
    }
    private String safeFileName(String value) {
        String result = text(value).replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        return result.isEmpty() ? "项目" : result;
    }
    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : Objects.toString(user.getUsername(), "-");
    }
    private void requireSingle(int affected, String action) {
        if (affected != 1) throw BusinessException.of(409, action + "状态已变化，请刷新后重试");
    }
    private BusinessException stateConflict(String message) { return BusinessException.of(409, message); }

    private record LocationEvidence(String result, Integer distanceMeters, Integer accuracyMeters,
                                    Integer projectVersion) {
        static LocationEvidence manual() { return new LocationEvidence(LOCATION_MANUAL, null, null, null); }
    }

    public record ExportFile(String fileName, byte[] content) {}
}
