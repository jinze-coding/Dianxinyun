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
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import com.example.siteplatform.siteaccess.entity.SiteVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.entity.SiteVisitPerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitPersonMapper;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
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
public class SiteAccessService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_VOIDED = "VOIDED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String PERSON_CONTACT = "CONTACT";
    public static final String PERSON_COMPANION = "COMPANION";
    public static final String TRAVEL_DRIVING = "DRIVING";
    public static final String TRAVEL_OTHER = "OTHER";
    private static final int MAX_VISITORS = 50;
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter INVITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private final SiteVisitInvitationMapper invitationMapper;
    private final SiteVisitPersonMapper personMapper;
    private final SiteVisitAuditLogMapper auditLogMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final ProjectPermissionService projectPermissionService;
    private final VisitorDataCryptoService cryptoService;
    private final WechatPlatformClient wechatPlatformClient;
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String miniProgramPage;
    private final String miniProgramEnvVersion;

    public SiteAccessService(
            SiteVisitInvitationMapper invitationMapper,
            SiteVisitPersonMapper personMapper,
            SiteVisitAuditLogMapper auditLogMapper,
            ProjectInfoMapper projectInfoMapper,
            SysUserMapper userMapper,
            SysUserProjectMapper userProjectMapper,
            ProjectPermissionService projectPermissionService,
            VisitorDataCryptoService cryptoService,
            WechatPlatformClient wechatPlatformClient,
            OperationLogMapper operationLogMapper,
            ObjectMapper objectMapper,
            @Value("${wechat.mini-program.visitor-page:pages/public/visitor-invite}") String miniProgramPage,
            @Value("${wechat.mini-program.env-version:release}") String miniProgramEnvVersion) {
        this.invitationMapper = invitationMapper;
        this.personMapper = personMapper;
        this.auditLogMapper = auditLogMapper;
        this.projectInfoMapper = projectInfoMapper;
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.projectPermissionService = projectPermissionService;
        this.cryptoService = cryptoService;
        this.wechatPlatformClient = wechatPlatformClient;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.miniProgramPage = miniProgramPage;
        this.miniProgramEnvVersion = miniProgramEnvVersion;
    }

    public PageResult<SiteVisitInvitationVO> page(Long projectId, String status, String keyword,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Integer pageNo, Integer pageSize, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_VIEW);
        DateRange range = normalizeOptionalRange(startDate, endDate);
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        Page<SiteVisitInvitation> result = invitationMapper.selectPage(
                new Page<>(current, size), query(projectId, status, keyword, range));
        ProjectInfo project = requireProject(projectId);
        return PageResult.of(current, size, result.getTotal(), result.getRecords().stream()
                .map(item -> toVO(item, project, false))
                .toList());
    }

    public SiteVisitInvitationVO detail(Long id, SysUser currentUser) {
        SiteVisitInvitation invitation = requireInvitation(id);
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        return toVO(invitation, requireProject(invitation.getProjectId()), true);
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
        invitation.setProjectId(request.getProjectId());
        invitation.setInviteNo(generateInviteNo(request.getVisitStartTime(), request.getVisitEndTime()));
        invitation.setTokenHash(cryptoService.digest(rawToken));
        invitation.setTokenEncrypted(cryptoService.encrypt(rawToken));
        invitation.setStatus(STATUS_PENDING);
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
        writeAudit(invitation, "CREATE", currentUser, null, snapshot(invitation), "创建单次外访邀请");
        recordOperation(currentUser, "CREATE_SITE_VISIT", invitation, "创建外访邀请 " + invitation.getInviteNo());
        return toVO(invitation, project, true);
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
        validateVisitTime(request.getVisitStartTime(), request.getVisitEndTime(), STATUS_PENDING.equals(invitation.getStatus()));
        SysUser host = requireHost(invitation.getProjectId(), request.getHostUserId(), currentUser);
        Map<String, Object> before = snapshot(invitation);
        if (!Objects.equals(invitation.getVisitStartTime(), request.getVisitStartTime())
                || !Objects.equals(invitation.getVisitEndTime(), request.getVisitEndTime())) {
            invitation.setInviteNo(rebuildInviteNo(
                    request.getVisitStartTime(), request.getVisitEndTime(), invitation.getInviteNo()));
        }
        copyMeetingFields(invitation, request.getVisitStartTime(), request.getVisitEndTime(),
                request.getPurpose(), request.getVisitLocation(), host, request.getInternalRemark());
        if (STATUS_SUBMITTED.equals(invitation.getStatus())) {
            NormalizedSubmission submission = normalizeSubmission(
                    request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                    request.getContactIdCard(), request.getCompanions(), request.getTravelMode(),
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
        Map<String, Object> after = snapshot(invitation);
        writeAudit(invitation, "UPDATE", currentUser, before, after, "修改外访邀请或来访信息");
        recordOperation(currentUser, "UPDATE_SITE_VISIT", invitation, "修改外访邀请 " + invitation.getInviteNo());
        return toVO(invitation, requireProject(invitation.getProjectId()), true);
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
        return toVO(invitation, requireProject(invitation.getProjectId()), true);
    }

    public SiteVisitMiniCodeVO miniCode(Long id, SysUser currentUser) {
        SiteVisitInvitation invitation = requireInvitation(id);
        requirePermission(currentUser, invitation.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        if (!STATUS_PENDING.equals(effectiveStatus(invitation))) {
            throw stateConflict("只有待填写邀请可以生成小程序码");
        }
        String token = cryptoService.decrypt(invitation.getTokenEncrypted());
        String scene = "V:" + token;
        String image = wechatPlatformClient.generateUnlimitedCode(scene, miniProgramPage, miniProgramEnvVersion);
        SiteVisitMiniCodeVO vo = new SiteVisitMiniCodeVO();
        vo.setInvitationId(invitation.getId());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setSceneCode(scene);
        vo.setPagePath(miniProgramPage);
        vo.setCodeType(image == null ? "DEVELOPMENT_SCENE" : "WECHAT_MINI_PROGRAM_CODE");
        vo.setImageMimeType(image == null ? null : "image/png");
        vo.setImageContent(image);
        vo.setHint(image == null
                ? "当前环境未配置正式微信小程序凭据，请在开发者工具使用 scene 调试"
                : "专属单次外访小程序码，可转发给本次来访联系人");
        return vo;
    }

    public PublicSiteVisitInvitationVO resolvePublic(String token) {
        SiteVisitInvitation invitation = findByToken(token, false);
        ProjectInfo project = requireProject(invitation.getProjectId());
        PublicSiteVisitInvitationVO vo = new PublicSiteVisitInvitationVO();
        vo.setInviteNo(invitation.getInviteNo());
        vo.setStatus(effectiveStatus(invitation));
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setVisitStartTime(invitation.getVisitStartTime());
        vo.setVisitEndTime(invitation.getVisitEndTime());
        vo.setPurpose(invitation.getPurpose());
        vo.setVisitLocation(invitation.getVisitLocation());
        vo.setHostName(invitation.getHostName());
        vo.setHostPhone(cryptoService.decrypt(invitation.getHostPhoneEncrypted()));
        return vo;
    }

    @Transactional
    public PublicSiteVisitInvitationVO submitPublic(PublicSiteVisitSubmitRequest request) {
        if (request == null) throw new BusinessException("外访登记参数不能为空");
        String normalizedToken = normalizeToken(request.getInviteToken());
        SiteVisitInvitation invitation = invitationMapper.selectForUpdateByTokenHash(cryptoService.digest(normalizedToken));
        if (invitation == null) throw BusinessException.notFound("邀请不存在或已失效");
        String status = effectiveStatus(invitation);
        if (STATUS_SUBMITTED.equals(status)) throw stateConflict("本次邀请已经提交，不能重复填写");
        if (STATUS_VOIDED.equals(status)) throw stateConflict("本次邀请已作废");
        if (STATUS_EXPIRED.equals(status)) throw stateConflict("本次邀请已过期");
        NormalizedSubmission submission = normalizeSubmission(
                request.getVisitorCompany(), request.getContactName(), request.getContactPhone(),
                request.getContactIdCard(), request.getCompanions(), request.getTravelMode(),
                request.getVehiclePlate(), request.getVisitorRemark());
        if (!Boolean.TRUE.equals(request.getPrivacyAgreed())) throw new BusinessException("请阅读并同意隐私告知");
        Map<String, Object> before = snapshot(invitation);
        applySubmissionFields(invitation, submission, true);
        replacePersons(invitation, submission);
        invitation.setStatus(STATUS_SUBMITTED);
        invitation.setSubmittedTime(LocalDateTime.now());
        invitation.setPrivacyAgreedTime(LocalDateTime.now());
        invitation.setVersion(versionOf(invitation) + 1);
        invitation.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(invitationMapper.updateById(invitation), "外访登记提交");
        writeAudit(invitation, "SUBMIT", null, before, snapshot(invitation), "外访联系人提交登记");
        return resolvePublic(normalizedToken);
    }

    public ExportFile export(Long projectId, String status, String keyword, LocalDate startDate,
                             LocalDate endDate, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_EXPORT);
        DateRange range = requireExportRange(startDate, endDate);
        String exportStatus = StringUtils.hasText(status) ? normalizeStatus(status) : STATUS_SUBMITTED;
        List<SiteVisitInvitation> invitations = invitationMapper.selectList(
                query(projectId, exportStatus, keyword, range));
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

    private LambdaQueryWrapper<SiteVisitInvitation> query(Long projectId, String status, String keyword, DateRange range) {
        LambdaQueryWrapper<SiteVisitInvitation> wrapper = new LambdaQueryWrapper<SiteVisitInvitation>()
                .eq(SiteVisitInvitation::getProjectId, projectId)
                .orderByAsc(SiteVisitInvitation::getVisitStartTime)
                .orderByDesc(SiteVisitInvitation::getId);
        if (StringUtils.hasText(status)) {
            String normalized = normalizeStatus(status);
            if (STATUS_EXPIRED.equals(normalized)) {
                wrapper.eq(SiteVisitInvitation::getStatus, STATUS_PENDING)
                        .lt(SiteVisitInvitation::getVisitEndTime, LocalDateTime.now());
            } else if (STATUS_PENDING.equals(normalized)) {
                wrapper.eq(SiteVisitInvitation::getStatus, STATUS_PENDING)
                        .ge(SiteVisitInvitation::getVisitEndTime, LocalDateTime.now());
            } else {
                wrapper.eq(SiteVisitInvitation::getStatus, normalized);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("查询关键词不能超过100个字符");
            wrapper.and(item -> item.like(SiteVisitInvitation::getInviteNo, value)
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

    private void applySubmissionFields(SiteVisitInvitation invitation, NormalizedSubmission submission,
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

    private void replacePersons(SiteVisitInvitation invitation, NormalizedSubmission submission) {
        personMapper.delete(new LambdaQueryWrapper<SiteVisitPerson>()
                .eq(SiteVisitPerson::getInvitationId, invitation.getId()));
        int order = 1;
        for (NormalizedPerson value : submission.people()) {
            SiteVisitPerson person = new SiteVisitPerson();
            person.setInvitationId(invitation.getId());
            person.setProjectId(invitation.getProjectId());
            person.setPersonType(value.personType());
            person.setPersonName(value.personName());
            person.setIdCardEncrypted(cryptoService.encrypt(value.idCard()));
            person.setIdCardHash(cryptoService.digest(value.idCard()));
            person.setSortOrder(order++);
            person.setDeleted(0);
            person.setCreateTime(LocalDateTime.now());
            person.setUpdateTime(LocalDateTime.now());
            requireSingleWrite(personMapper.insert(person), "来访人员写入");
        }
    }

    private NormalizedSubmission normalizeSubmission(String company, String contactName, String contactPhone,
                                                      String contactIdCard, List<SiteVisitPersonRequest> companions,
                                                      String travelMode, String vehiclePlate, String visitorRemark) {
        String normalizedCompany = requiredText(company, 200, "外访单位");
        String normalizedContactName = requiredText(contactName, 50, "主联系人姓名");
        String normalizedPhone = requiredText(contactPhone, 11, "主联系人手机号");
        if (!normalizedPhone.matches("^1[3-9]\\d{9}$")) throw new BusinessException("手机号格式不正确");
        String normalizedTravelMode = requiredText(travelMode, 20, "出行方式").toUpperCase(Locale.ROOT);
        if (!Set.of(TRAVEL_DRIVING, TRAVEL_OTHER).contains(normalizedTravelMode)) {
            throw new BusinessException("出行方式不正确");
        }
        String normalizedPlate = normalizePlate(vehiclePlate);
        if (TRAVEL_DRIVING.equals(normalizedTravelMode) && !StringUtils.hasText(normalizedPlate)) {
            throw new BusinessException("驾车来访必须填写车牌号");
        }
        if (TRAVEL_OTHER.equals(normalizedTravelMode)) normalizedPlate = null;
        List<NormalizedPerson> people = new ArrayList<>();
        people.add(new NormalizedPerson(PERSON_CONTACT, normalizedContactName,
                normalizeAndValidateIdCard(contactIdCard)));
        for (SiteVisitPersonRequest companion : companions == null ? List.<SiteVisitPersonRequest>of() : companions) {
            if (companion == null) throw new BusinessException("同行人员信息不能为空");
            people.add(new NormalizedPerson(PERSON_COMPANION,
                    requiredText(companion.getPersonName(), 50, "同行人员姓名"),
                    normalizeAndValidateIdCard(companion.getIdCard())));
        }
        if (people.size() > MAX_VISITORS) throw new BusinessException("一次来访最多登记50名人员");
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (NormalizedPerson person : people) {
            if (!uniqueIds.add(person.idCard())) throw new BusinessException("同一次来访不能重复登记同一身份证号");
        }
        return new NormalizedSubmission(normalizedCompany, normalizedContactName, normalizedPhone,
                normalizedTravelMode, normalizedPlate, optionalText(visitorRemark, 500, "外访备注"), people);
    }

    private String normalizeAndValidateIdCard(String raw) {
        String value = requiredText(raw, 18, "身份证号").toUpperCase(Locale.ROOT);
        if (!value.matches("^\\d{17}[0-9X]$")) throw new BusinessException("身份证号格式不正确");
        try {
            LocalDate.parse(value.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException exception) {
            throw new BusinessException("身份证号出生日期不正确");
        }
        int sum = 0;
        for (int index = 0; index < 17; index++) {
            sum += (value.charAt(index) - '0') * ID_CARD_WEIGHTS[index];
        }
        if (ID_CARD_CHECK[sum % 11] != value.charAt(17)) throw new BusinessException("身份证号校验失败");
        return value;
    }

    private String normalizePlate(String raw) {
        String value = trimToNull(raw);
        if (value == null) return null;
        value = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.length() < 2 || value.length() > 20) throw new BusinessException("车牌号长度不正确");
        return value;
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

    private String normalizeToken(String rawToken) {
        String token = requiredText(rawToken, 64, "邀请令牌");
        if (token.startsWith("V:")) token = token.substring(2);
        if (!token.matches("^[A-Za-z0-9_-]{20,32}$")) throw BusinessException.notFound("邀请不存在或已失效");
        return token;
    }

    private String effectiveStatus(SiteVisitInvitation invitation) {
        if (STATUS_PENDING.equals(invitation.getStatus())
                && invitation.getVisitEndTime() != null
                && invitation.getVisitEndTime().isBefore(LocalDateTime.now())) return STATUS_EXPIRED;
        return invitation.getStatus();
    }

    private SiteVisitInvitationVO toVO(SiteVisitInvitation invitation, ProjectInfo project, boolean detail) {
        SiteVisitInvitationVO vo = new SiteVisitInvitationVO();
        vo.setId(invitation.getId());
        vo.setProjectId(invitation.getProjectId());
        vo.setProjectName(project.getProjectName());
        vo.setInviteNo(invitation.getInviteNo());
        vo.setStatus(effectiveStatus(invitation));
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
        vo.setPersonName(person.getPersonName());
        vo.setIdCard(cryptoService.decrypt(person.getIdCardEncrypted()));
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
        result.put("visitors", persons(invitation.getId()).stream().map(person -> Map.of(
                "personType", person.getPersonType(),
                "personName", person.getPersonName(),
                "idCard", cryptoService.decrypt(person.getIdCardEncrypted()),
                "sortOrder", person.getSortOrder())).toList());
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
                "外访单位", "人员类型", "姓名", "身份证号", "主联系人手机号", "出行方式", "车牌号",
                "接待人", "接待人手机号", "状态", "提交时间"};
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
                            nullToEmpty(invitation.getVisitorCompany()),
                            PERSON_CONTACT.equals(person.getPersonType()) ? "主联系人" : "同行人员",
                            nullToEmpty(person.getPersonName()),
                            nullToEmpty(cryptoService.decrypt(person.getIdCardEncrypted())),
                            nullToEmpty(cryptoService.decrypt(invitation.getContactPhoneEncrypted())),
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
            int[] widths = {24, 20, 18, 18, 28, 22, 24, 12, 14, 22, 18, 12, 16, 14, 18, 12, 18};
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
        if (!Set.of(STATUS_PENDING, STATUS_SUBMITTED, STATUS_EXPIRED, STATUS_VOIDED).contains(value)) {
            throw new BusinessException("外访状态不正确");
        }
        return value;
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

    private record NormalizedPerson(String personType, String personName, String idCard) {}
    private record NormalizedSubmission(String visitorCompany, String contactName, String contactPhone,
                                        String travelMode, String vehiclePlate, String visitorRemark,
                                        List<NormalizedPerson> people) {}
    private record DateRange(LocalDate start, LocalDate end) {}
    public record ExportFile(String fileName, byte[] content) {}
}
