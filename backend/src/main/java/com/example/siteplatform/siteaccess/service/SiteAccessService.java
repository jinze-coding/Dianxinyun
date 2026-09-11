package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectCoordinateConverter;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicVisitorSessionCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import com.example.siteplatform.siteaccess.entity.SiteVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.entity.SiteVisitPerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.PublicProjectLocationVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitAuditVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitHostOptionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitPersonVO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SiteAccessService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_VOIDED = "VOIDED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String PERSON_CONTACT = "CONTACT";
    public static final String PERSON_COMPANION = "COMPANION";
    public static final String TRAVEL_DRIVING = "DRIVING";
    public static final String TRAVEL_OTHER = "OTHER";
    public static final String INVITE_TYPE_SINGLE = "SINGLE";
    public static final String INVITE_TYPE_MEETING = "MEETING";
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter INVITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SiteVisitInvitationMapper invitationMapper;
    private final SiteVisitPersonMapper personMapper;
    private final SiteVisitAuditLogMapper auditLogMapper;
    private final SiteMeetingVisitRegistrationMapper meetingRegistrationMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectProfileService projectProfileService;
    private final ProjectRouteImageService projectRouteImageService;
    private final VisitorDataCryptoService cryptoService;
    private final WechatPlatformClient wechatPlatformClient;
    private final VisitorSessionService visitorSessionService;
    private final VisitorProfileService visitorProfileService;
    private final VisitorPersonalProfileService personalProfileService;
    private final MeetingCheckinQrProvisioner meetingCheckinQrProvisioner;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String miniProgramPage;
    private final String meetingMiniProgramPage;
    private final String miniProgramEnvVersion;

    public SiteAccessService(
            SiteVisitInvitationMapper invitationMapper,
            SiteVisitPersonMapper personMapper,
            SiteVisitAuditLogMapper auditLogMapper,
            SiteMeetingVisitRegistrationMapper meetingRegistrationMapper,
            ProjectInfoMapper projectInfoMapper,
            SysUserMapper userMapper,
            SysUserProjectMapper userProjectMapper,
            ProjectPermissionService projectPermissionService,
            ProjectProfileService projectProfileService,
            ProjectRouteImageService projectRouteImageService,
            VisitorDataCryptoService cryptoService,
            WechatPlatformClient wechatPlatformClient,
            VisitorSessionService visitorSessionService,
            VisitorProfileService visitorProfileService,
            VisitorPersonalProfileService personalProfileService,
            MeetingCheckinQrProvisioner meetingCheckinQrProvisioner,
            OperationLogMapper operationLogMapper,
            ObjectMapper objectMapper,
            @Value("${wechat.mini-program.visitor-page:pages/public/visitor-invite}") String miniProgramPage,
            @Value("${wechat.mini-program.meeting-visitor-page:pages/public/meeting-invite}") String meetingMiniProgramPage,
            @Value("${wechat.mini-program.env-version:release}") String miniProgramEnvVersion) {
        this.invitationMapper = invitationMapper;
        this.personMapper = personMapper;
        this.auditLogMapper = auditLogMapper;
        this.meetingRegistrationMapper = meetingRegistrationMapper;
        this.projectInfoMapper = projectInfoMapper;
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.projectPermissionService = projectPermissionService;
        this.projectProfileService = projectProfileService;
        this.projectRouteImageService = projectRouteImageService;
        this.cryptoService = cryptoService;
        this.wechatPlatformClient = wechatPlatformClient;
        this.visitorSessionService = visitorSessionService;
        this.visitorProfileService = visitorProfileService;
        this.personalProfileService = personalProfileService;
        this.meetingCheckinQrProvisioner = meetingCheckinQrProvisioner;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.miniProgramPage = miniProgramPage;
        this.meetingMiniProgramPage = meetingMiniProgramPage;
        this.miniProgramEnvVersion = miniProgramEnvVersion;
    }

    public PageResult<SiteVisitInvitationVO> page(Long projectId, String inviteType, String status, String keyword,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Integer pageNo, Integer pageSize, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_VIEW);
        DateRange range = normalizeOptionalRange(startDate, endDate);
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        Page<SiteVisitInvitation> result = invitationMapper.selectPage(
                new Page<>(current, size), query(projectId, inviteType, status, keyword, range));
        ProjectInfo project = requireProject(projectId);
        Map<Long, long[]> meetingStats = meetingStats(result.getRecords());
        return PageResult.of(current, size, result.getTotal(), result.getRecords().stream()
                .map(item -> toVO(item, project, false, meetingStats.get(item.getId())))
                .toList());
    }

    public SiteVisitInvitationVO detail(Long id, SysUser currentUser) {
        SiteVisitInvitation invitation = requireInvitation(id);
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        return toVO(invitation, requireProject(invitation.getProjectId()), true,
                meetingStats(List.of(invitation)).get(invitation.getId()));
    }

    public List<SiteVisitHostOptionVO> hostOptions(Long projectId, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_MANAGE);
        requireProject(projectId);
        Map<Long, SiteVisitHostOptionVO> result = new LinkedHashMap<>();
        for (ProjectMemberVO member : userProjectMapper.selectMembersByProjectId(projectId)) {
            if (!"ACTIVE".equalsIgnoreCase(member.getAccessStatus()) || !Integer.valueOf(1).equals(member.getStatus())) continue;
            result.put(member.getUserId(), new SiteVisitHostOptionVO(
                    member.getUserId(), displayName(member.getRealName(), member.getUsername()), member.getPhone()));
        }
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            result.putIfAbsent(currentUser.getId(), new SiteVisitHostOptionVO(
                    currentUser.getId(), displayName(currentUser), currentUser.getPhone()));
        }
        return result.values().stream()
                .sorted(Comparator.comparing(SiteVisitHostOptionVO::getRealName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional
    public SiteVisitInvitationVO create(SiteVisitInvitationCreateRequest request, SysUser currentUser) {
        if (request == null) throw new BusinessException("邀请参数不能为空");
        validateVisitTime(request.getVisitStartTime(), request.getVisitEndTime(), true);
        requirePermission(currentUser, request.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        ProjectInfo project = requireProjectForUpdate(request.getProjectId());
        SysUser host = requireHost(request.getProjectId(), request.getHostUserId(), currentUser);
        String rawToken = generateToken();
        SiteVisitInvitation invitation = new SiteVisitInvitation();
        String inviteType = normalizeInviteType(request.getInviteType());
        invitation.setProjectId(request.getProjectId());
        invitation.setInviteNo(generateInviteNo(request.getVisitStartTime(), request.getVisitEndTime()));
        invitation.setTokenHash(cryptoService.digest(rawToken));
        invitation.setTokenEncrypted(cryptoService.encrypt(rawToken));
        invitation.setInviteType(inviteType);
        invitation.setStatus(INVITE_TYPE_MEETING.equals(inviteType) ? STATUS_OPEN : STATUS_PENDING);
        copyMeetingFields(invitation, request.getVisitStartTime(), request.getVisitEndTime(),
                request.getPurpose(), request.getVisitLocation(), host, request.getInternalRemark());
        invitation.setVisitorCount(0);
        invitation.setCreatedById(currentUser.getId());
        invitation.setCreatedByName(displayName(currentUser));
        invitation.setVersion(0);
        invitation.setDeleted(0);
        invitation.setCreateTime(LocalDateTime.now());
        invitation.setUpdateTime(LocalDateTime.now());
        try {
            requireSingleWrite(invitationMapper.insert(invitation), "邀请创建");
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.of(409, "邀请编号生成冲突，请重试");
        }
        writeAudit(invitation, "CREATE", currentUser, null, snapshot(invitation),
                INVITE_TYPE_MEETING.equals(inviteType) ? "创建共享会议邀请" : "创建单次外访邀请");
        if (INVITE_TYPE_MEETING.equals(inviteType)) {
            meetingCheckinQrProvisioner.provision(invitation, currentUser);
        }
        recordOperation(currentUser, "CREATE_SITE_VISIT", invitation, "创建外访邀请 " + invitation.getInviteNo());
        return toVO(invitation, project, true, null);
    }

    @Transactional
    public SiteVisitInvitationVO update(Long id, SiteVisitInvitationUpdateRequest request, SysUser currentUser) {
        if (request == null) throw new BusinessException("修改参数不能为空");
        SiteVisitInvitation invitation = invitationMapper.selectForUpdate(id);
        if (invitation == null) throw BusinessException.notFound("外访邀请不存在");
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        String effectiveStatus = effectiveStatus(invitation);
        if (STATUS_VOIDED.equals(effectiveStatus) || STATUS_EXPIRED.equals(effectiveStatus)) {
            throw stateConflict("已作废或已过期邀请不能修改，请重新创建邀请");
        }
        validateVisitTime(request.getVisitStartTime(), request.getVisitEndTime(),
                STATUS_PENDING.equals(invitation.getStatus()) || STATUS_OPEN.equals(invitation.getStatus()));
        SysUser host = requireHost(invitation.getProjectId(), request.getHostUserId(), currentUser);
        Map<String, Object> before = snapshot(invitation);
        LocalDateTime previousStartTime = invitation.getVisitStartTime();
        LocalDateTime previousEndTime = invitation.getVisitEndTime();
        boolean meetingTimeChanged = !Objects.equals(previousStartTime, request.getVisitStartTime())
                || !Objects.equals(previousEndTime, request.getVisitEndTime());
        if (meetingTimeChanged) {
            invitation.setInviteNo(rebuildInviteNo(
                    request.getVisitStartTime(), request.getVisitEndTime(), invitation.getInviteNo()));
        }
        copyMeetingFields(invitation, request.getVisitStartTime(), request.getVisitEndTime(),
                request.getPurpose(), request.getVisitLocation(), host, request.getInternalRemark());
        if (STATUS_SUBMITTED.equals(invitation.getStatus()) && isSingle(invitation)) {
            VisitorSubmissionNormalizer.Submission submission = normalizeSubmission(
                    request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                    request.getCompanions(), request.getTravelMode(),
                    request.getVehiclePlate(), request.getVisitorRemark());
            applySubmissionFields(invitation, submission, false);
            replacePersons(invitation, submission);
        }
        invitation.setVersion(versionOf(invitation) + 1);
        invitation.setUpdateTime(LocalDateTime.now());
        try {
            requireSingleWrite(invitationMapper.updateById(invitation), "邀请修改");
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.of(409, "邀请编号生成冲突，请重试");
        }
        if (isMeeting(invitation) && meetingTimeChanged) {
            meetingCheckinQrProvisioner.reconcileMeetingTimeChange(
                    invitation, previousStartTime, previousEndTime, currentUser);
        }
        Map<String, Object> after = snapshot(invitation);
        writeAudit(invitation, "UPDATE", currentUser, before, after, "修改外访邀请或来访信息");
        recordOperation(currentUser, "UPDATE_SITE_VISIT", invitation, "修改外访邀请 " + invitation.getInviteNo());
        return toVO(invitation, requireProject(invitation.getProjectId()), true,
                meetingStats(List.of(invitation)).get(invitation.getId()));
    }

    @Transactional
    public SiteVisitInvitationVO voidInvitation(Long id, String reason, SysUser currentUser) {
        SiteVisitInvitation invitation = invitationMapper.selectForUpdate(id);
        if (invitation == null) throw BusinessException.notFound("外访邀请不存在");
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        String effectiveStatus = effectiveStatus(invitation);
        if (STATUS_VOIDED.equals(effectiveStatus)) throw stateConflict("邀请已经作废");
        if (STATUS_EXPIRED.equals(effectiveStatus)) throw stateConflict("已过期邀请不能作废，请重新创建邀请");
        String normalizedReason = requiredText(reason, 300, "作废原因");
        Map<String, Object> before = snapshot(invitation);
        invitation.setStatus(STATUS_VOIDED);
        invitation.setVoidReason(normalizedReason);
        invitation.setVoidedById(currentUser.getId());
        invitation.setVoidedByName(displayName(currentUser));
        invitation.setVoidedTime(LocalDateTime.now());
        invitation.setVersion(versionOf(invitation) + 1);
        invitation.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(invitationMapper.updateById(invitation), "邀请作废");
        writeAudit(invitation, "VOID", currentUser, before, snapshot(invitation), normalizedReason);
        recordOperation(currentUser, "VOID_SITE_VISIT", invitation, "作废外访邀请 " + invitation.getInviteNo());
        return toVO(invitation, requireProject(invitation.getProjectId()), true,
                meetingStats(List.of(invitation)).get(invitation.getId()));
    }

    public SiteVisitMiniCodeVO miniCode(Long id, SysUser currentUser) {
        SiteVisitInvitation invitation = requireInvitation(id);
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        String status = invitation.getStatus();
        boolean allowedStatus = isMeeting(invitation)
                ? STATUS_OPEN.equals(status)
                : STATUS_PENDING.equals(status) || STATUS_SUBMITTED.equals(status);
        if (!allowedStatus
                || invitation.getVisitEndTime() == null
                || !invitation.getVisitEndTime().isAfter(LocalDateTime.now())) {
            throw stateConflict("只有仍在计划来访时段内的邀请可以查看小程序码");
        }
        String token = cryptoService.decrypt(invitation.getTokenEncrypted());
        String pagePath = isMeeting(invitation) ? meetingMiniProgramPage : miniProgramPage;
        String scene = (isMeeting(invitation) ? "M:" : "V:") + token;
        String image = wechatPlatformClient.generateUnlimitedCode(scene, pagePath, miniProgramEnvVersion);
        SiteVisitMiniCodeVO vo = new SiteVisitMiniCodeVO();
        vo.setInvitationId(invitation.getId());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setInviteType(inviteTypeOf(invitation));
        vo.setSceneCode(scene);
        vo.setPagePath(pagePath);
        vo.setCodeType(image == null ? "DEVELOPMENT_SCENE" : "WECHAT_MINI_PROGRAM_CODE");
        vo.setImageMimeType(image == null ? null : "image/png");
        vo.setImageContent(image);
        String hint;
        if (isMeeting(invitation)) {
            hint = image == null
                    ? "当前环境使用 scene 调试；共享会议码要求每位访客通过微信身份独立登记"
                    : "共享会议小程序码；截止前每个微信身份可独立登记并查看自己的放行凭证";
        } else if (image == null) {
            hint = STATUS_SUBMITTED.equals(status)
                    ? "当前环境使用 scene 调试；本次来访已登记，再次扫码可展示门卫放行凭证"
                    : "当前环境未配置正式微信小程序凭据，请在开发者工具使用 scene 调试";
        } else {
            hint = STATUS_SUBMITTED.equals(status)
                    ? "本次来访已登记，再次扫码可展示门卫放行凭证"
                    : "专属单次外访小程序码，可转发给本次来访人员";
        }
        vo.setHint(hint);
        return vo;
    }

    public PublicSiteVisitInvitationVO resolvePublic(String token) {
        SiteVisitInvitation invitation = findByToken(token, false);
        ProjectInfo project = requireProject(invitation.getProjectId());
        LocalDateTime now = LocalDateTime.now();
        PublicSiteVisitInvitationVO vo = new PublicSiteVisitInvitationVO();
        vo.setInviteNo(invitation.getInviteNo());
        vo.setInviteType(inviteTypeOf(invitation));
        vo.setStatus(publicStatus(invitation, now));
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setVisitEndTime(invitation.getVisitEndTime());
        vo.setPurpose(invitation.getPurpose());
        vo.setVisitLocation(invitation.getVisitLocation());
        vo.setHostName(invitation.getHostName());
        vo.setHostPhone(cryptoService.decrypt(invitation.getHostPhoneEncrypted()));
        vo.setVisitorCompany(invitation.getVisitorCompany());
        vo.setContactName(invitation.getContactName());
        vo.setVisitorCount(invitation.getVisitorCount());
        vo.setTravelMode(invitation.getTravelMode());
        vo.setVehiclePlate(invitation.getVehiclePlate());
        vo.setSubmittedTime(invitation.getSubmittedTime());
        vo.setServerTime(now);
        vo.setProjectLocation(publicProjectLocation(invitation, project, now));
        return vo;
    }

    public PublicVisitorSessionVO createVisitorSession(PublicVisitorSessionCreateRequest request) {
        if (request == null) throw new BusinessException("外访临时会话参数不能为空");
        SiteVisitInvitation invitation = findByToken(request.getInviteToken(), false);
        if (!isSingle(invitation)) throw BusinessException.of(403, "会议邀请必须使用微信身份登记会话");
        if (!STATUS_PENDING.equals(effectiveStatus(invitation))) {
            throw stateConflict("当前邀请不能获取微信登记会话");
        }
        PublicVisitorSessionVO issued = visitorSessionService.issue(request.getWechatCode(), invitation);
        issued.setPersonalInfo(personalProfileService.read(
                visitorSessionService.require(issued.getVisitorSessionToken(), invitation)));
        return issued;
    }

    public PublicProjectProfileVO publicProjectProfile(String inviteToken) {
        SiteVisitInvitation invitation = requirePublicProjectInvitation(inviteToken);
        return projectProfileService.getPublicProfile(invitation.getProjectId());
    }

    public ProjectProfileService.PublicProjectProfileImageContent publicProjectProfileImage(
            String inviteToken, Integer imageIndex) {
        SiteVisitInvitation invitation = requirePublicProjectInvitation(inviteToken);
        return projectProfileService.getPublicProfileImage(invitation.getProjectId(), imageIndex);
    }

    public ProjectRouteImageService.PublicProjectRouteImageContent publicProjectRouteImage(String inviteToken) {
        SiteVisitInvitation invitation = requirePublicProjectInvitation(inviteToken);
        return projectRouteImageService.publicImage(invitation.getProjectId());
    }

    public List<SiteVisitorProfileVO> publicVisitorProfiles(String visitorSessionToken) {
        VisitorSessionService.VisitorSessionContext context = requirePendingVisitorSession(visitorSessionToken);
        return visitorProfileService.publicList(context);
    }

    public SiteVisitorProfileVO publicVisitorProfile(String visitorSessionToken, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requirePendingVisitorSession(visitorSessionToken);
        return visitorProfileService.publicDetail(context, profileCode);
    }

    public void disablePublicVisitorProfile(String visitorSessionToken, String profileCode) {
        VisitorSessionService.VisitorSessionContext context = requirePendingVisitorSession(visitorSessionToken);
        visitorProfileService.publicDisable(context, profileCode);
    }

    public PageResult<SiteVisitorProfileVO> visitorProfiles(Long projectId, String status, String keyword,
                                                            Integer pageNo, Integer pageSize, SysUser currentUser) {
        return visitorProfileService.internalPage(projectId, status, keyword, pageNo, pageSize, currentUser);
    }

    public SiteVisitorProfileVO visitorProfileDetail(Long id, SysUser currentUser) {
        return visitorProfileService.internalDetail(id, currentUser);
    }

    public SiteVisitorProfileVO disableVisitorProfile(Long id, SysUser currentUser) {
        return visitorProfileService.internalDisable(id, currentUser);
    }

    @Transactional
    public PublicSiteVisitInvitationVO submitPublic(PublicSiteVisitSubmitRequest request) {
        return submitPublic(request, null);
    }

    @Transactional
    public PublicSiteVisitInvitationVO submitPublic(PublicSiteVisitSubmitRequest request,
                                                     String visitorSessionToken) {
        if (request == null) throw new BusinessException("外访登记参数不能为空");
        String normalizedToken = normalizeToken(request.getInviteToken());
        SiteVisitInvitation resolved = findByToken(normalizedToken, false);
        if (projectInfoMapper.selectByIdForUpdate(resolved.getProjectId()) == null) throw BusinessException.notFound("项目不存在");
        SiteVisitInvitation invitation = invitationMapper.selectForUpdateByTokenHash(cryptoService.digest(normalizedToken));
        if (invitation == null) throw BusinessException.notFound("邀请不存在或已失效");
        if (!isSingle(invitation)) throw BusinessException.of(403, "会议邀请不能使用单次预约提交接口");
        String status = effectiveStatus(invitation);
        if (STATUS_SUBMITTED.equals(status)) {
            // 只有原微信身份的重试可取得原回执；不重复写入，也不覆盖更新的本人信息。
            if (!StringUtils.hasText(invitation.getWechatAppId()) || !StringUtils.hasText(invitation.getVisitorIdentityHash())) {
                throw stateConflict("本次邀请已经提交，不能重复填写");
            }
            var owner = visitorSessionService.require(visitorSessionToken, invitation);
            if (!Objects.equals(invitation.getWechatAppId(), owner.appId())
                    || !Objects.equals(invitation.getVisitorIdentityHash(), VisitorIdentitySupport.hash(
                            "single-registration", owner, cryptoService, visitorSessionService))) {
                throw BusinessException.of(403, "本次邀请已由其他微信提交");
            }
            return resolvePublic(normalizedToken);
        }
        if (STATUS_VOIDED.equals(status)) throw stateConflict("本次邀请已作废");
        if (STATUS_EXPIRED.equals(status)) throw stateConflict("本次邀请已过期");
        VisitorSubmissionNormalizer.Submission submission = normalizeSubmission(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getCompanions(), request.getTravelMode(),
                request.getVehiclePlate(), request.getVisitorRemark());
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) throw new BusinessException("请阅读并同意隐私告知");
        Map<String, Object> before = snapshot(invitation);
        VisitorSessionService.VisitorSessionContext context = visitorSessionService.require(visitorSessionToken, invitation);
        Long sourceProfileId = visitorProfileService.applyOnSubmission(
                context, request.getProfileAction(), request.getProfileCode(), request.getProfileName(),
                request.getProfileRetentionAgreed(), request.getProfileVersion(),
                toProfileSubmission(submission));
        applySubmissionFields(invitation, submission, true);
        replacePersons(invitation, submission);
        invitation.setSourceProfileId(sourceProfileId);
        invitation.setWechatAppId(context.appId());
        invitation.setVisitorIdentityHash(VisitorIdentitySupport.hash("single-registration", context, cryptoService, visitorSessionService));
        personalProfileService.saveOnSubmission(context, request.getRememberInfo(), submission);
        invitation.setStatus(STATUS_SUBMITTED);
        invitation.setSubmittedTime(LocalDateTime.now());
        invitation.setPrivacyAgreedTime(LocalDateTime.now());
        invitation.setVersion(versionOf(invitation) + 1);
        invitation.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(invitationMapper.updateById(invitation), "外访登记提交");
        writeAudit(invitation, "SUBMIT", null, before, snapshot(invitation), "访客提交登记");
        return resolvePublic(normalizedToken);
    }

    public ExportFile export(Long projectId, String status, String keyword, LocalDate startDate,
                             LocalDate endDate, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_EXPORT);
        DateRange range = requireExportRange(startDate, endDate);
        String exportStatus = StringUtils.hasText(status) ? normalizeStatus(status) : STATUS_SUBMITTED;
        List<SiteVisitInvitation> invitations = invitationMapper.selectList(
                query(projectId, INVITE_TYPE_SINGLE, exportStatus, keyword, range));
        List<Long> invitationIds = invitations.stream().map(SiteVisitInvitation::getId).toList();
        Map<Long, List<SiteVisitPerson>> peopleByInvitation = invitationIds.isEmpty() ? Map.of()
                : personMapper.selectList(new LambdaQueryWrapper<SiteVisitPerson>()
                        .in(SiteVisitPerson::getInvitationId, invitationIds)
                        .orderByAsc(SiteVisitPerson::getInvitationId)
                        .orderByAsc(SiteVisitPerson::getSortOrder))
                .stream().collect(Collectors.groupingBy(SiteVisitPerson::getInvitationId,
                        LinkedHashMap::new, Collectors.toList()));
        int rows = peopleByInvitation.values().stream().mapToInt(List::size).sum();
        if (rows > MAX_EXPORT_ROWS) throw new BusinessException("导出人员超过50000人，请缩小日期范围");
        ProjectInfo project = requireProject(projectId);
        byte[] content = buildWorkbook(project, invitations, peopleByInvitation);
        writeProjectAudit(projectId, "EXPORT", currentUser,
                Map.of("startDate", range.start().toString(), "endDate", range.end().toString(),
                        "status", exportStatus, "visitorRows", rows),
                "导出外访人员 " + range.start() + " 至 " + range.end() + "，共" + rows + "人");
        recordOperation(currentUser, "EXPORT_SITE_VISIT", projectId,
                "导出场内管理外访人员，日期范围 " + range.start() + " 至 " + range.end() + "，共" + rows + "人");
        String projectName = safeFileName(StringUtils.hasText(project.getShortName())
                ? project.getShortName() : project.getProjectName());
        String fileName = "场内管理_外访人员_" + projectName + "_"
                + FILE_DATE.format(range.start()) + "-" + FILE_DATE.format(range.end()) + ".xlsx";
        return new ExportFile(fileName, content);
    }

    private LambdaQueryWrapper<SiteVisitInvitation> query(Long projectId, String inviteType, String status,
                                                          String keyword, DateRange range) {
        LambdaQueryWrapper<SiteVisitInvitation> wrapper = new LambdaQueryWrapper<SiteVisitInvitation>()
                .eq(SiteVisitInvitation::getProjectId, projectId)
                .orderByAsc(SiteVisitInvitation::getVisitStartTime)
                .orderByDesc(SiteVisitInvitation::getId);
        if (StringUtils.hasText(inviteType)) {
            wrapper.eq(SiteVisitInvitation::getInviteType, normalizeInviteType(inviteType));
        }
        if (StringUtils.hasText(status)) {
            String normalized = normalizeStatus(status);
            if (STATUS_EXPIRED.equals(normalized)) {
                wrapper.in(SiteVisitInvitation::getStatus, STATUS_PENDING, STATUS_OPEN)
                        .le(SiteVisitInvitation::getVisitEndTime, LocalDateTime.now());
            } else if (STATUS_PENDING.equals(normalized)) {
                wrapper.eq(SiteVisitInvitation::getStatus, STATUS_PENDING)
                        .ge(SiteVisitInvitation::getVisitEndTime, LocalDateTime.now());
            } else if (STATUS_OPEN.equals(normalized)) {
                wrapper.eq(SiteVisitInvitation::getStatus, STATUS_OPEN)
                        .gt(SiteVisitInvitation::getVisitEndTime, LocalDateTime.now());
            } else {
                wrapper.eq(SiteVisitInvitation::getStatus, normalized);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("查询关键词不能超过100个字符");
            wrapper.and(item -> item.like(SiteVisitInvitation::getInviteNo, value)
                    .or().like(SiteVisitInvitation::getPurpose, value)
                    .or().like(SiteVisitInvitation::getVisitorCompany, value)
                    .or().like(SiteVisitInvitation::getContactName, value)
                    .or().like(SiteVisitInvitation::getVehiclePlate, value)
                    .or().like(SiteVisitInvitation::getHostName, value));
        }
        if (range != null) {
            wrapper.ge(SiteVisitInvitation::getVisitStartTime, range.start().atStartOfDay())
                    .lt(SiteVisitInvitation::getVisitStartTime, range.end().plusDays(1).atStartOfDay());
        }
        return wrapper;
    }

    private void copyMeetingFields(SiteVisitInvitation target, LocalDateTime start, LocalDateTime end,
                                   String purpose, String location, SysUser host, String internalRemark) {
        target.setVisitStartTime(start);
        target.setVisitEndTime(end);
        target.setPurpose(requiredText(purpose, 300, "来访事由"));
        target.setVisitLocation(requiredText(location, 200, "到访地点"));
        target.setHostUserId(host.getId());
        target.setHostName(displayName(host));
        target.setHostPhoneEncrypted(cryptoService.encrypt(trimToNull(host.getPhone())));
        target.setInternalRemark(optionalText(internalRemark, 500, "内部备注"));
    }

    private void applySubmissionFields(SiteVisitInvitation invitation, VisitorSubmissionNormalizer.Submission submission,
                                       boolean firstSubmission) {
        invitation.setVisitorCompany(submission.visitorCompany());
        invitation.setContactName(submission.contactName());
        invitation.setContactPhoneEncrypted(cryptoService.encrypt(submission.contactPhone()));
        invitation.setVisitorCount(submission.people().size());
        invitation.setTravelMode(submission.travelMode());
        invitation.setVehiclePlate(submission.vehiclePlate());
        invitation.setVisitorRemark(submission.visitorRemark());
        if (firstSubmission) invitation.setPrivacyAgreedTime(LocalDateTime.now());
    }

    private void replacePersons(SiteVisitInvitation invitation, VisitorSubmissionNormalizer.Submission submission) {
        personMapper.delete(new LambdaQueryWrapper<SiteVisitPerson>()
                .eq(SiteVisitPerson::getInvitationId, invitation.getId()));
        int order = 1;
        for (VisitorSubmissionNormalizer.Person value : submission.people()) {
            SiteVisitPerson person = new SiteVisitPerson();
            person.setInvitationId(invitation.getId());
            person.setProjectId(invitation.getProjectId());
            person.setPersonType(value.personType());
            person.setPersonCompany(value.personCompany());
            person.setPersonName(value.personName());
            person.setPhoneEncrypted(cryptoService.encrypt(value.personPhone()));
            person.setIdCardEncrypted(null);
            person.setIdCardHash(null);
            person.setSortOrder(order++);
            person.setDeleted(0);
            person.setCreateTime(LocalDateTime.now());
            person.setUpdateTime(LocalDateTime.now());
            requireSingleWrite(personMapper.insert(person), "来访人员写入");
        }
    }

    private VisitorSubmissionNormalizer.Submission normalizeSubmission(
            String company, String contactName, String contactPhone,
            List<SiteVisitPersonRequest> companions, String travelMode,
            String vehiclePlate, String visitorRemark) {
        return VisitorSubmissionNormalizer.normalize(company, contactName, contactPhone,
                companions, travelMode, vehiclePlate, visitorRemark);
    }

    private void validateVisitTime(LocalDateTime start, LocalDateTime end, boolean requireFutureEnd) {
        if (start == null || end == null) throw new BusinessException("请选择完整的计划到场和离场时间");
        if (!start.isBefore(end)) throw new BusinessException("计划到场时间必须早于计划离场时间");
        if (ChronoUnit.DAYS.between(start, end) > 31) throw new BusinessException("单次来访时间跨度不能超过31天");
        if (requireFutureEnd && !end.isAfter(LocalDateTime.now())) throw new BusinessException("计划离场时间必须晚于当前时间");
    }

    private SiteVisitInvitation findByToken(String rawToken, boolean forUpdate) {
        String token = normalizeToken(rawToken);
        String hash = cryptoService.digest(token);
        SiteVisitInvitation invitation = forUpdate
                ? invitationMapper.selectForUpdateByTokenHash(hash)
                : invitationMapper.selectOne(new LambdaQueryWrapper<SiteVisitInvitation>()
                .eq(SiteVisitInvitation::getTokenHash, hash).last("LIMIT 1"));
        if (invitation == null) throw BusinessException.notFound("邀请不存在或已失效");
        return invitation;
    }

    private VisitorSessionService.VisitorSessionContext requirePendingVisitorSession(String token) {
        VisitorSessionService.VisitorSessionContext context = visitorSessionService.require(token);
        SiteVisitInvitation invitation = invitationMapper.selectById(context.invitationId());
        if (invitation == null || !Objects.equals(invitation.getProjectId(), context.projectId())) {
            throw BusinessException.of(401, "外访临时会话无效，请重新打开邀请");
        }
        if (!STATUS_PENDING.equals(effectiveStatus(invitation))) {
            throw stateConflict("当前邀请不能继续管理常用资料");
        }
        return context;
    }

    private VisitorProfileService.SubmissionData toProfileSubmission(VisitorSubmissionNormalizer.Submission submission) {
        return new VisitorProfileService.SubmissionData(
                submission.visitorCompany(), submission.contactName(), submission.contactPhone(),
                submission.travelMode(), submission.vehiclePlate(), submission.people().stream()
                .map(person -> new VisitorProfileService.PersonData(
                        person.personType(), person.personCompany(), person.personName(), person.personPhone()))
                .toList());
    }

    private SiteVisitInvitation requirePublicProjectInvitation(String inviteToken) {
        SiteVisitInvitation invitation = findByToken(inviteToken, false);
        String status = invitation.getStatus();
        if (STATUS_VOIDED.equals(status)
                || invitation.getVisitEndTime() == null
                || !invitation.getVisitEndTime().isAfter(LocalDateTime.now())
                || (!STATUS_PENDING.equals(status) && !STATUS_SUBMITTED.equals(status) && !STATUS_OPEN.equals(status))) {
            throw stateConflict("当前邀请不可查看项目信息");
        }
        return invitation;
    }

    private String normalizeToken(String rawToken) {
        String token = requiredText(rawToken, 64, "邀请令牌");
        if (token.startsWith("V:") || token.startsWith("M:")) token = token.substring(2);
        if (!token.matches("^[A-Za-z0-9_-]{20,32}$")) throw BusinessException.notFound("邀请不存在或已失效");
        return token;
    }

    private String effectiveStatus(SiteVisitInvitation invitation) {
        if ((STATUS_PENDING.equals(invitation.getStatus()) || STATUS_OPEN.equals(invitation.getStatus()))
                && invitation.getVisitEndTime() != null
                && !invitation.getVisitEndTime().isAfter(LocalDateTime.now())) return STATUS_EXPIRED;
        return invitation.getStatus();
    }

    private String publicStatus(SiteVisitInvitation invitation, LocalDateTime now) {
        if ((STATUS_PENDING.equals(invitation.getStatus()) || STATUS_SUBMITTED.equals(invitation.getStatus())
                || STATUS_OPEN.equals(invitation.getStatus()))
                && invitation.getVisitEndTime() != null
                && !invitation.getVisitEndTime().isAfter(now)) return STATUS_EXPIRED;
        return invitation.getStatus();
    }

    private PublicProjectLocationVO publicProjectLocation(SiteVisitInvitation invitation,
                                                          ProjectInfo project,
                                                          LocalDateTime now) {
        String status = invitation.getStatus();
        if ((!STATUS_PENDING.equals(status) && !STATUS_SUBMITTED.equals(status) && !STATUS_OPEN.equals(status))
                || invitation.getVisitEndTime() == null
                || !invitation.getVisitEndTime().isAfter(now)) {
            return null;
        }
        PublicProjectLocationVO location = new PublicProjectLocationVO();
        location.setAddress(project.getAddress());
        location.setCoordinateType("GCJ02");
        location.setRouteImageAvailable(projectRouteImageService.hasActiveImage(project.getId()));
        var converted = ProjectCoordinateConverter.toGcj02(
                project.getLongitude(), project.getLatitude(), project.getCoordinateType());
        location.setNavigable(converted.isPresent());
        converted.ifPresent(coordinate -> {
            location.setLongitude(coordinate.longitude());
            location.setLatitude(coordinate.latitude());
        });
        return location;
    }

    private SiteVisitInvitationVO toVO(SiteVisitInvitation invitation, ProjectInfo project,
                                       boolean detail, long[] meetingStats) {
        SiteVisitInvitationVO vo = new SiteVisitInvitationVO();
        vo.setId(invitation.getId());
        vo.setProjectId(invitation.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setInviteType(inviteTypeOf(invitation));
        vo.setStatus(effectiveStatus(invitation));
        vo.setRegistrationGroupCount(meetingStats == null ? 0L : meetingStats[0]);
        vo.setRegisteredPersonCount(meetingStats == null ? 0L : meetingStats[1]);
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setVisitEndTime(invitation.getVisitEndTime());
        vo.setPurpose(invitation.getPurpose());
        vo.setVisitLocation(invitation.getVisitLocation());
        vo.setHostUserId(invitation.getHostUserId());
        vo.setHostName(invitation.getHostName());
        vo.setInternalRemark(invitation.getInternalRemark());
        vo.setVisitorCompany(invitation.getVisitorCompany());
        vo.setContactName(invitation.getContactName());
        vo.setVisitorCount(invitation.getVisitorCount());
        vo.setTravelMode(invitation.getTravelMode());
        vo.setVehiclePlate(invitation.getVehiclePlate());
        vo.setVisitorRemark(invitation.getVisitorRemark());
        vo.setSourceProfileId(invitation.getSourceProfileId());
        vo.setSourceProfileName(visitorProfileService.sourceName(invitation.getSourceProfileId()));
        vo.setSubmittedTime(invitation.getSubmittedTime());
        vo.setVoidReason(invitation.getVoidReason());
        vo.setCreatedById(invitation.getCreatedById());
        vo.setCreatedByName(invitation.getCreatedByName());
        vo.setCreateTime(invitation.getCreateTime());
        vo.setUpdateTime(invitation.getUpdateTime());
        if (detail) {
            vo.setHostPhone(cryptoService.decrypt(invitation.getHostPhoneEncrypted()));
            vo.setContactPhone(cryptoService.decrypt(invitation.getContactPhoneEncrypted()));
            vo.setVisitors(persons(invitation.getId()).stream().map(this::toPersonVO).toList());
            vo.setAuditLogs(auditLogMapper.selectList(new LambdaQueryWrapper<SiteVisitAuditLog>()
                            .eq(SiteVisitAuditLog::getInvitationId, invitation.getId())
                            .orderByAsc(SiteVisitAuditLog::getCreateTime)
                            .orderByAsc(SiteVisitAuditLog::getId))
                    .stream().map(this::toAuditVO).toList());
        }
        return vo;
    }

    private List<SiteVisitPerson> persons(Long invitationId) {
        return personMapper.selectList(new LambdaQueryWrapper<SiteVisitPerson>()
                .eq(SiteVisitPerson::getInvitationId, invitationId)
                .orderByAsc(SiteVisitPerson::getSortOrder)
                .orderByAsc(SiteVisitPerson::getId));
    }

    private SiteVisitPersonVO toPersonVO(SiteVisitPerson person) {
        SiteVisitPersonVO vo = new SiteVisitPersonVO();
        vo.setId(person.getId());
        vo.setPersonType(person.getPersonType());
        vo.setPersonCompany(person.getPersonCompany());
        vo.setPersonName(person.getPersonName());
        vo.setPersonPhone(cryptoService.decrypt(person.getPhoneEncrypted()));
        vo.setSortOrder(person.getSortOrder());
        return vo;
    }

    private SiteVisitAuditVO toAuditVO(SiteVisitAuditLog log) {
        SiteVisitAuditVO vo = new SiteVisitAuditVO();
        vo.setId(log.getId());
        vo.setActionType(log.getActionType());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setComment(log.getComment());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private Map<String, Object> snapshot(SiteVisitInvitation invitation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inviteNo", invitation.getInviteNo());
        result.put("inviteType", inviteTypeOf(invitation));
        result.put("status", effectiveStatus(invitation));
        result.put("visitStartTime", invitation.getVisitStartTime());
        result.put("visitEndTime", invitation.getVisitEndTime());
        result.put("purpose", invitation.getPurpose());
        result.put("visitLocation", invitation.getVisitLocation());
        result.put("hostUserId", invitation.getHostUserId());
        result.put("hostName", invitation.getHostName());
        result.put("hostPhone", cryptoService.decrypt(invitation.getHostPhoneEncrypted()));
        result.put("internalRemark", invitation.getInternalRemark());
        result.put("visitorCompany", invitation.getVisitorCompany());
        result.put("contactName", invitation.getContactName());
        result.put("contactPhone", cryptoService.decrypt(invitation.getContactPhoneEncrypted()));
        result.put("visitorCount", invitation.getVisitorCount());
        result.put("travelMode", invitation.getTravelMode());
        result.put("vehiclePlate", invitation.getVehiclePlate());
        result.put("visitorRemark", invitation.getVisitorRemark());
        result.put("sourceProfileId", invitation.getSourceProfileId());
        result.put("visitors", persons(invitation.getId()).stream().map(person -> {
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

    private void writeAudit(SiteVisitInvitation invitation, String action, SysUser operator,
                            Map<String, Object> before, Map<String, Object> after, String comment) {
        SiteVisitAuditLog log = new SiteVisitAuditLog();
        log.setInvitationId(invitation.getId());
        log.setProjectId(invitation.getProjectId());
        log.setActionType(action);
        log.setOperatorId(operator == null ? null : operator.getId());
        log.setOperatorName(operator == null ? "外访人员" : displayName(operator));
        log.setBeforeSnapshotEncrypted(encryptSnapshot(before));
        log.setAfterSnapshotEncrypted(encryptSnapshot(after));
        log.setComment(optionalText(comment, 500, "审计说明"));
        log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(auditLogMapper.insert(log), "外访审计日志写入");
    }

    private void writeProjectAudit(Long projectId, String action, SysUser operator,
                                   Map<String, Object> after, String comment) {
        SiteVisitAuditLog log = new SiteVisitAuditLog();
        log.setProjectId(projectId);
        log.setActionType(action);
        log.setOperatorId(operator.getId());
        log.setOperatorName(displayName(operator));
        log.setAfterSnapshotEncrypted(encryptSnapshot(after));
        log.setComment(optionalText(comment, 500, "审计说明"));
        log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(auditLogMapper.insert(log), "外访导出审计日志写入");
    }

    private String encryptSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("外访审计快照生成失败", exception);
        }
    }

    private void recordOperation(SysUser user, String action, SiteVisitInvitation invitation, String description) {
        recordOperation(user, action, invitation.getId(), description);
    }

    private void recordOperation(SysUser user, String action, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(action);
        log.setOperationDesc(description);
        log.setBusinessType("SITE_ACCESS");
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(operationLogMapper.insert(log), "系统操作日志写入");
    }

    private byte[] buildWorkbook(ProjectInfo project, List<SiteVisitInvitation> invitations,
                                 Map<Long, List<SiteVisitPerson>> peopleByInvitation) {
        String[] headers = {"项目", "邀请编号", "计划到场", "计划离场", "来访事由", "到访地点",
                "单位", "人员类型", "姓名", "手机号码", "出行方式", "车牌号",
                "接待人", "接待人手机号码", "状态", "提交时间"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("外访人员");
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
            for (SiteVisitInvitation invitation : invitations) {
                for (SiteVisitPerson person : peopleByInvitation.getOrDefault(invitation.getId(), List.of())) {
                    Row row = sheet.createRow(rowNumber++);
                    List<String> values = List.of(
                            nullToEmpty(project.getProjectName()),
                            nullToEmpty(invitation.getInviteNo()),
                            formatDateTime(invitation.getVisitStartTime()),
                            formatDateTime(invitation.getVisitEndTime()),
                            nullToEmpty(invitation.getPurpose()),
                            nullToEmpty(invitation.getVisitLocation()),
                            nullToEmpty(PERSON_CONTACT.equals(person.getPersonType())
                                    ? preferredOrFallback(person.getPersonCompany(), invitation.getVisitorCompany())
                                    : person.getPersonCompany()),
                            PERSON_CONTACT.equals(person.getPersonType()) ? "本人" : "同行人员",
                            nullToEmpty(person.getPersonName()),
                            nullToEmpty(PERSON_CONTACT.equals(person.getPersonType())
                                    ? preferredOrFallback(cryptoService.decrypt(person.getPhoneEncrypted()),
                                    cryptoService.decrypt(invitation.getContactPhoneEncrypted()))
                                    : cryptoService.decrypt(person.getPhoneEncrypted())),
                            TRAVEL_DRIVING.equals(invitation.getTravelMode()) ? "驾车" : "非驾车",
                            nullToEmpty(invitation.getVehiclePlate()),
                            nullToEmpty(invitation.getHostName()),
                            nullToEmpty(cryptoService.decrypt(invitation.getHostPhoneEncrypted())),
                            statusLabel(effectiveStatus(invitation)),
                            formatDateTime(invitation.getSubmittedTime()));
                    for (int index = 0; index < values.size(); index++) {
                        row.createCell(index).setCellValue(safeExcelText(values.get(index)));
                    }
                }
            }
            int[] widths = {24, 20, 18, 18, 28, 22, 24, 12, 14, 18, 12, 16, 14, 18, 12, 18};
            for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("外访人员 Excel 生成失败");
        }
    }

    private String safeExcelText(String value) {
        if (value == null) return "";
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case STATUS_PENDING -> "待填写";
            case STATUS_SUBMITTED -> "已提交";
            case STATUS_OPEN -> "开放登记";
            case STATUS_EXPIRED -> "已过期";
            case STATUS_VOIDED -> "已作废";
            default -> status;
        };
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
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > 366) {
            throw new BusinessException("日期范围不能超过366天");
        }
        return new DateRange(startDate, endDate);
    }

    private void requirePermission(SysUser user, Long projectId, String permissionCode) {
        if (projectId == null || projectId <= 0) throw new BusinessException("项目ID不能为空");
        projectPermissionService.requireSystemPermission(user.getId(), projectId, permissionCode);
    }

    private ProjectInfo requireProject(Long projectId) {
        ProjectInfo project = projectInfoMapper.selectById(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    /** Serializes invitation creation with project deletion to prevent orphaned visitor data. */
    private ProjectInfo requireProjectForUpdate(Long projectId) {
        ProjectInfo project = projectInfoMapper.selectByIdForUpdate(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    private SiteVisitInvitation requireInvitation(Long id) {
        SiteVisitInvitation invitation = id == null ? null : invitationMapper.selectById(id);
        if (invitation == null) throw BusinessException.notFound("外访邀请不存在");
        return invitation;
    }

    private SysUser requireHost(Long projectId, Long hostUserId, SysUser operator) {
        if (hostUserId == null) throw new BusinessException("请选择接待人");
        SysUser host = userMapper.selectById(hostUserId);
        if (host == null || Integer.valueOf(1).equals(host.getDeleted()) || !Integer.valueOf(1).equals(host.getStatus())) {
            throw new BusinessException("接待人账号不存在或已停用");
        }
        boolean operatorPlatformSelf = Objects.equals(operator.getId(), hostUserId)
                && projectPermissionService.isPlatformAdmin(operator.getId());
        SysUserProject membership = userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getUserId, hostUserId)
                .eq(SysUserProject::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (membership == null && !operatorPlatformSelf) throw new BusinessException("接待人不是当前项目有效成员");
        return host;
    }

    private String generateToken() {
        byte[] value = new byte[16];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    String generateInviteNo(LocalDateTime visitStartTime, LocalDateTime visitEndTime) {
        return formatInviteNo(visitStartTime, visitEndTime,
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
    }

    String rebuildInviteNo(
            LocalDateTime visitStartTime, LocalDateTime visitEndTime, String currentInviteNo) {
        String suffix = currentInviteNo == null
                ? ""
                : currentInviteNo.substring(currentInviteNo.lastIndexOf('-') + 1).toUpperCase(Locale.ROOT);
        if (!suffix.matches("[0-9A-F]{8}")) {
            suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        }
        return formatInviteNo(visitStartTime, visitEndTime, suffix);
    }

    private String formatInviteNo(LocalDateTime visitStartTime, LocalDateTime visitEndTime, String suffix) {
        return "VIS-" + INVITE_DATE_TIME.format(visitStartTime) + "-"
                + INVITE_DATE_TIME.format(visitEndTime) + "-"
                + suffix;
    }

    private int versionOf(SiteVisitInvitation invitation) {
        return invitation.getVersion() == null ? 0 : invitation.getVersion();
    }

    private String requiredText(String value, int max, String field) {
        String result = trimToNull(value);
        if (result == null) throw new BusinessException(field + "不能为空");
        if (result.length() > max) throw new BusinessException(field + "不能超过" + max + "个字符");
        return result;
    }

    private String optionalText(String value, int max, String field) {
        String result = trimToNull(value);
        if (result != null && result.length() > max) throw new BusinessException(field + "不能超过" + max + "个字符");
        return result;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(STATUS_PENDING, STATUS_SUBMITTED, STATUS_OPEN, STATUS_EXPIRED, STATUS_VOIDED).contains(value)) {
            throw new BusinessException("外访状态不正确");
        }
        return value;
    }

    private String normalizeInviteType(String inviteType) {
        String value = StringUtils.hasText(inviteType)
                ? inviteType.trim().toUpperCase(Locale.ROOT) : INVITE_TYPE_SINGLE;
        if (!Set.of(INVITE_TYPE_SINGLE, INVITE_TYPE_MEETING).contains(value)) {
            throw new BusinessException("邀请类型不正确");
        }
        return value;
    }

    private String inviteTypeOf(SiteVisitInvitation invitation) {
        return INVITE_TYPE_MEETING.equalsIgnoreCase(invitation.getInviteType())
                ? INVITE_TYPE_MEETING : INVITE_TYPE_SINGLE;
    }

    private boolean isMeeting(SiteVisitInvitation invitation) {
        return INVITE_TYPE_MEETING.equals(inviteTypeOf(invitation));
    }

    private boolean isSingle(SiteVisitInvitation invitation) {
        return !isMeeting(invitation);
    }

    private Map<Long, long[]> meetingStats(List<SiteVisitInvitation> invitations) {
        List<Long> ids = invitations.stream().filter(this::isMeeting)
                .map(SiteVisitInvitation::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, long[]> result = new LinkedHashMap<>();
        for (Map<String, Object> row : meetingRegistrationMapper.selectActiveStats(ids)) {
            Long invitationId = ((Number) row.get("invitationId")).longValue();
            long groups = ((Number) row.get("registrationGroupCount")).longValue();
            long people = ((Number) row.get("registeredPersonCount")).longValue();
            result.put(invitationId, new long[]{groups, people});
        }
        return result;
    }

    private String displayName(SysUser user) {
        return displayName(user.getRealName(), user.getUsername());
    }

    private String displayName(String realName, String username) {
        return StringUtils.hasText(realName) ? realName.trim() : Objects.toString(username, "-");
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DISPLAY_DATE_TIME.format(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String preferredOrFallback(String preferred, String fallback) {
        return preferred == null ? fallback : preferred;
    }

    private String safeFileName(String value) {
        String result = Objects.toString(value, "项目").replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        return result.isEmpty() ? "项目" : result;
    }

    private void requireSingleWrite(int affected, String action) {
        if (affected != 1) throw BusinessException.of(409, action + "状态已变化，请刷新后重试");
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message);
    }

    private record DateRange(LocalDate start, LocalDate end) {}
    public record ExportFile(String fileName, byte[] content) {}
}
