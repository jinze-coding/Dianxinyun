package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfile;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfileAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfilePerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfileAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfileMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfilePersonMapper;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfilePersonVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class VisitorProfileService {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String ACTION_NONE = "NONE";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    private static final int MAX_ACTIVE_PROFILES = 20;

    private final SiteVisitorProfileMapper profileMapper;
    private final SiteVisitorProfilePersonMapper personMapper;
    private final SiteVisitorProfileAuditLogMapper auditMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final ProjectPermissionService permissionService;
    private final VisitorDataCryptoService cryptoService;
    private final VisitorSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public VisitorProfileService(SiteVisitorProfileMapper profileMapper,
                                 SiteVisitorProfilePersonMapper personMapper,
                                 SiteVisitorProfileAuditLogMapper auditMapper,
                                 ProjectInfoMapper projectInfoMapper,
                                 ProjectPermissionService permissionService,
                                 VisitorDataCryptoService cryptoService,
                                 VisitorSessionService sessionService,
                                 ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.personMapper = personMapper;
        this.auditMapper = auditMapper;
        this.projectInfoMapper = projectInfoMapper;
        this.permissionService = permissionService;
        this.cryptoService = cryptoService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    public List<SiteVisitorProfileVO> publicList(VisitorSessionService.VisitorSessionContext context) {
        return profileMapper.selectList(ownedQuery(context)
                        .eq(SiteVisitorProfile::getStatus, STATUS_ACTIVE)
                        .orderByDesc(SiteVisitorProfile::getLastUsedTime)
                        .orderByDesc(SiteVisitorProfile::getId)
                        .last("LIMIT " + MAX_ACTIVE_PROFILES))
                .stream().map(profile -> toVO(profile, false)).toList();
    }

    public SiteVisitorProfileVO publicDetail(VisitorSessionService.VisitorSessionContext context,
                                              String profileCode) {
        SiteVisitorProfile profile = requireOwned(context, profileCode, false);
        if (!STATUS_ACTIVE.equals(profile.getStatus())) throw stateConflict("该常用资料已停用");
        return toVO(profile, true);
    }

    @Transactional
    public void publicDisable(VisitorSessionService.VisitorSessionContext context, String profileCode) {
        SiteVisitorProfile profile = requireOwned(context, profileCode, true);
        disable(profile, null, "访客停用常用资料");
    }

    public PageResult<SiteVisitorProfileVO> internalPage(Long projectId, String status, String keyword,
                                                         Integer pageNo, Integer pageSize, SysUser currentUser) {
        requirePermission(currentUser, projectId, SystemPermissionCodes.SITE_ACCESS_VIEW);
        int current = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        LambdaQueryWrapper<SiteVisitorProfile> query = new LambdaQueryWrapper<SiteVisitorProfile>()
                .eq(SiteVisitorProfile::getProjectId, projectId)
                .orderByDesc(SiteVisitorProfile::getLastUsedTime)
                .orderByDesc(SiteVisitorProfile::getId);
        if (StringUtils.hasText(status)) query.eq(SiteVisitorProfile::getStatus, normalizeStatus(status));
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException("查询关键词不能超过100个字符");
            query.and(item -> item.like(SiteVisitorProfile::getProfileCode, value)
                    .or().like(SiteVisitorProfile::getProfileName, value)
                    .or().like(SiteVisitorProfile::getVisitorCompany, value)
                    .or().like(SiteVisitorProfile::getContactName, value)
                    .or().like(SiteVisitorProfile::getVehiclePlate, value));
        }
        Page<SiteVisitorProfile> result = profileMapper.selectPage(new Page<>(current, size), query);
        return PageResult.of(current, size, result.getTotal(),
                result.getRecords().stream().map(profile -> toVO(profile, false)).toList());
    }

    public SiteVisitorProfileVO internalDetail(Long id, SysUser currentUser) {
        SiteVisitorProfile profile = requireProfile(id);
        requirePermission(currentUser, profile.getProjectId(), SystemPermissionCodes.SITE_ACCESS_VIEW);
        return toVO(profile, true);
    }

    public String sourceName(Long id) {
        if (id == null) return null;
        SiteVisitorProfile profile = profileMapper.selectById(id);
        return profile == null ? "已删除的常用资料" : profile.getProfileName();
    }

    @Transactional
    public SiteVisitorProfileVO internalDisable(Long id, SysUser currentUser) {
        SiteVisitorProfile current = requireProfile(id);
        requirePermission(currentUser, current.getProjectId(), SystemPermissionCodes.SITE_ACCESS_MANAGE);
        SiteVisitorProfile profile = profileMapper.selectForUpdateByCode(current.getProfileCode());
        if (profile == null) throw BusinessException.notFound("常用资料不存在");
        disable(profile, currentUser, "项目人员停用常用资料");
        return toVO(profile, true);
    }

    /** Called inside the invitation submit transaction; returns the immutable source profile id. */
    @Transactional
    public Long applyOnSubmission(VisitorSessionService.VisitorSessionContext context,
                                  String actionValue, String profileCode, String profileName,
                                  Boolean retentionAgreed, Integer expectedVersion,
                                  SubmissionData submission) {
        String action = normalizeAction(actionValue);
        String normalizedCode = trimToNull(profileCode);
        if (ACTION_NONE.equals(action) && normalizedCode == null) return null;
        if (context == null) throw BusinessException.of(401, "保存或使用常用资料需要重新获取微信身份");
        if (ACTION_CREATE.equals(action)) {
            if (normalizedCode != null) throw new BusinessException("新建常用资料时不能指定已有资料");
            requireRetentionConsent(retentionAgreed);
            return create(context, profileName, submission).getId();
        }
        if (normalizedCode == null) throw new BusinessException("请选择要使用的常用资料");
        SiteVisitorProfile profile = requireOwned(context, normalizedCode, true);
        if (!STATUS_ACTIVE.equals(profile.getStatus())) throw stateConflict("该常用资料已停用");
        if (ACTION_UPDATE.equals(action)) {
            if (expectedVersion == null) {
                throw new BusinessException("更新常用资料必须提供当前版本号");
            }
            if (!Objects.equals(expectedVersion, versionOf(profile))) {
                throw stateConflict("常用资料已更新，请重新选择后提交");
            }
        }
        Map<String, Object> before = snapshot(profile);
        if (ACTION_UPDATE.equals(action)) {
            requireRetentionConsent(retentionAgreed);
            applyFields(profile, profileName, submission);
            replacePeople(profile, submission.people());
            profile.setVersion(versionOf(profile) + 1);
        }
        profile.setLastUsedTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());
        requireSingle(profileMapper.updateById(profile), "常用资料使用");
        writeAudit(profile, ACTION_UPDATE.equals(action) ? "UPDATE" : "USE", null,
                before, snapshot(profile), ACTION_UPDATE.equals(action) ? "提交时更新常用资料" : "提交时使用常用资料");
        return profile.getId();
    }

    private SiteVisitorProfile create(VisitorSessionService.VisitorSessionContext context,
                                      String profileName, SubmissionData submission) {
        // Every creator in one project takes the same durable row lock before
        // counting. This makes the 20-profile ceiling independent of MySQL's
        // transaction isolation level and avoids relying on gap locks.
        if (projectInfoMapper.selectByIdForUpdate(context.projectId()) == null) {
            throw BusinessException.notFound("项目不存在");
        }
        List<SiteVisitorProfile> activeProfiles = profileMapper.selectActiveOwnerProfilesForUpdate(
                context.projectId(), context.appId(), context.identityHash());
        if (activeProfiles.size() >= MAX_ACTIVE_PROFILES) {
            throw new BusinessException("当前项目最多保存20份常用资料，请先停用不再使用的资料");
        }
        SiteVisitorProfile profile = new SiteVisitorProfile();
        profile.setProfileCode(generateCode());
        profile.setProjectId(context.projectId());
        profile.setWechatAppId(context.appId());
        profile.setOwnerOpenidEncrypted(cryptoService.encrypt(sessionService.decryptOpenid(context)));
        profile.setOwnerOpenidHash(context.identityHash());
        profile.setStatus(STATUS_ACTIVE);
        profile.setPrivacyAgreedTime(LocalDateTime.now());
        profile.setLastUsedTime(LocalDateTime.now());
        profile.setVersion(0);
        profile.setDeleted(0);
        profile.setCreateTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());
        applyFields(profile, profileName, submission);
        try {
            requireSingle(profileMapper.insert(profile), "常用资料创建");
        } catch (DuplicateKeyException exception) {
            throw stateConflict("常用资料创建冲突，请重试");
        }
        replacePeople(profile, submission.people());
        writeAudit(profile, "CREATE", null, null, snapshot(profile), "访客同意保存常用资料");
        return profile;
    }

    private void disable(SiteVisitorProfile profile, SysUser operator, String comment) {
        if (STATUS_DISABLED.equals(profile.getStatus())) throw stateConflict("该常用资料已经停用");
        Map<String, Object> before = snapshot(profile);
        profile.setStatus(STATUS_DISABLED);
        profile.setVersion(versionOf(profile) + 1);
        profile.setUpdateTime(LocalDateTime.now());
        requireSingle(profileMapper.updateById(profile), "常用资料停用");
        writeAudit(profile, "DISABLE", operator, before, snapshot(profile), comment);
    }

    private void applyFields(SiteVisitorProfile profile, String profileName, SubmissionData submission) {
        String normalizedName = trimToNull(profileName);
        if (normalizedName == null) normalizedName = submission.contactName() + "的常用资料";
        if (normalizedName.length() > 100) throw new BusinessException("常用资料名称不能超过100个字符");
        profile.setProfileName(normalizedName);
        profile.setVisitorCompany(submission.visitorCompany());
        profile.setContactName(submission.contactName());
        profile.setContactPhoneEncrypted(cryptoService.encrypt(submission.contactPhone()));
        profile.setVisitorCount(submission.people().size());
        profile.setTravelMode(submission.travelMode());
        profile.setVehiclePlate(submission.vehiclePlate());
    }

    private void replacePeople(SiteVisitorProfile profile, List<PersonData> people) {
        personMapper.delete(new LambdaQueryWrapper<SiteVisitorProfilePerson>()
                .eq(SiteVisitorProfilePerson::getProfileId, profile.getId()));
        int sort = 1;
        for (PersonData value : people) {
            SiteVisitorProfilePerson person = new SiteVisitorProfilePerson();
            person.setProfileId(profile.getId());
            person.setProjectId(profile.getProjectId());
            person.setPersonType(value.personType());
            person.setPersonName(value.personName());
            person.setIdCardEncrypted(cryptoService.encrypt(value.idCard()));
            person.setIdCardHash(cryptoService.idCardFingerprint(value.idCard()));
            person.setSortOrder(sort++);
            person.setDeleted(0);
            person.setCreateTime(LocalDateTime.now());
            person.setUpdateTime(LocalDateTime.now());
            requireSingle(personMapper.insert(person), "常用资料人员写入");
        }
    }

    private SiteVisitorProfile requireOwned(VisitorSessionService.VisitorSessionContext context,
                                             String profileCode, boolean lock) {
        String code = requireCode(profileCode);
        SiteVisitorProfile profile = lock ? profileMapper.selectForUpdateByCode(code)
                : profileMapper.selectOne(ownedQuery(context)
                        .eq(SiteVisitorProfile::getProfileCode, code).last("LIMIT 1"));
        if (profile == null || !Objects.equals(profile.getProjectId(), context.projectId())
                || !Objects.equals(profile.getWechatAppId(), context.appId())
                || !Objects.equals(profile.getOwnerOpenidHash(), context.identityHash())) {
            throw BusinessException.notFound("常用资料不存在");
        }
        return profile;
    }

    private LambdaQueryWrapper<SiteVisitorProfile> ownedQuery(
            VisitorSessionService.VisitorSessionContext context) {
        return new LambdaQueryWrapper<SiteVisitorProfile>()
                .eq(SiteVisitorProfile::getProjectId, context.projectId())
                .eq(SiteVisitorProfile::getWechatAppId, context.appId())
                .eq(SiteVisitorProfile::getOwnerOpenidHash, context.identityHash());
    }

    private SiteVisitorProfile requireProfile(Long id) {
        SiteVisitorProfile profile = id == null ? null : profileMapper.selectById(id);
        if (profile == null) throw BusinessException.notFound("常用资料不存在");
        return profile;
    }

    private SiteVisitorProfileVO toVO(SiteVisitorProfile profile, boolean detail) {
        SiteVisitorProfileVO vo = new SiteVisitorProfileVO();
        vo.setId(profile.getId());
        vo.setProfileCode(profile.getProfileCode());
        vo.setProjectId(profile.getProjectId());
        vo.setProfileName(profile.getProfileName());
        vo.setVisitorCompany(profile.getVisitorCompany());
        vo.setContactName(profile.getContactName());
        String phone = cryptoService.decrypt(profile.getContactPhoneEncrypted());
        vo.setMaskedContactPhone(maskPhone(phone));
        if (detail) vo.setContactPhone(phone);
        vo.setVisitorCount(profile.getVisitorCount());
        vo.setTravelMode(profile.getTravelMode());
        vo.setVehiclePlate(profile.getVehiclePlate());
        vo.setStatus(profile.getStatus());
        vo.setVersion(profile.getVersion());
        vo.setLastUsedTime(profile.getLastUsedTime());
        vo.setCreateTime(profile.getCreateTime());
        vo.setUpdateTime(profile.getUpdateTime());
        if (detail) vo.setPeople(people(profile.getId()).stream().map(this::toPersonVO).toList());
        return vo;
    }

    private SiteVisitorProfilePersonVO toPersonVO(SiteVisitorProfilePerson person) {
        SiteVisitorProfilePersonVO vo = new SiteVisitorProfilePersonVO();
        vo.setPersonType(person.getPersonType());
        vo.setPersonName(person.getPersonName());
        String idCard = cryptoService.decrypt(person.getIdCardEncrypted());
        vo.setIdCard(idCard);
        vo.setMaskedIdCard(maskIdCard(idCard));
        vo.setSortOrder(person.getSortOrder());
        return vo;
    }

    private List<SiteVisitorProfilePerson> people(Long profileId) {
        return personMapper.selectList(new LambdaQueryWrapper<SiteVisitorProfilePerson>()
                .eq(SiteVisitorProfilePerson::getProfileId, profileId)
                .orderByAsc(SiteVisitorProfilePerson::getSortOrder)
                .orderByAsc(SiteVisitorProfilePerson::getId));
    }

    private Map<String, Object> snapshot(SiteVisitorProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileCode", profile.getProfileCode());
        result.put("profileName", profile.getProfileName());
        result.put("status", profile.getStatus());
        result.put("visitorCompany", profile.getVisitorCompany());
        result.put("contactName", profile.getContactName());
        result.put("contactPhone", cryptoService.decrypt(profile.getContactPhoneEncrypted()));
        result.put("visitorCount", profile.getVisitorCount());
        result.put("travelMode", profile.getTravelMode());
        result.put("vehiclePlate", profile.getVehiclePlate());
        List<Map<String, Object>> people = new ArrayList<>();
        for (SiteVisitorProfilePerson person : people(profile.getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("personType", person.getPersonType());
            item.put("personName", person.getPersonName());
            item.put("idCard", cryptoService.decrypt(person.getIdCardEncrypted()));
            item.put("sortOrder", person.getSortOrder());
            people.add(item);
        }
        result.put("people", people);
        return result;
    }

    private void writeAudit(SiteVisitorProfile profile, String action, SysUser operator,
                            Map<String, Object> before, Map<String, Object> after, String comment) {
        SiteVisitorProfileAuditLog audit = new SiteVisitorProfileAuditLog();
        audit.setProfileId(profile.getId());
        audit.setProjectId(profile.getProjectId());
        audit.setActionType(action);
        audit.setOperatorId(operator == null ? null : operator.getId());
        audit.setOperatorName(operator == null ? "外访人员" : displayName(operator));
        audit.setBeforeSnapshotEncrypted(encryptSnapshot(before));
        audit.setAfterSnapshotEncrypted(encryptSnapshot(after));
        audit.setComment(comment);
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "常用资料审计写入");
    }

    private String encryptSnapshot(Map<String, Object> value) {
        if (value == null) return null;
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("常用资料审计快照生成失败", exception);
        }
    }

    private String normalizeAction(String action) {
        String value = trimToNull(action);
        value = value == null ? ACTION_NONE : value.toUpperCase(Locale.ROOT);
        if (!Set.of(ACTION_NONE, ACTION_CREATE, ACTION_UPDATE).contains(value)) {
            throw new BusinessException("常用资料操作不正确");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(STATUS_ACTIVE, STATUS_DISABLED).contains(value)) {
            throw new BusinessException("常用资料状态不正确");
        }
        return value;
    }

    private void requireRetentionConsent(Boolean agreed) {
        if (!Boolean.TRUE.equals(agreed)) throw new BusinessException("请确认同意保存常用资料");
    }

    private String requireCode(String value) {
        String code = trimToNull(value);
        if (code == null || code.length() > 40 || !code.matches("^[A-Za-z0-9_-]+$")) {
            throw BusinessException.notFound("常用资料不存在");
        }
        return code;
    }

    private String generateCode() {
        byte[] value = new byte[18];
        secureRandom.nextBytes(value);
        return "VP_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String maskPhone(String value) {
        return value != null && value.length() == 11
                ? value.substring(0, 3) + "****" + value.substring(7) : "***";
    }

    private String maskIdCard(String value) {
        return value != null && value.length() == 18
                ? value.substring(0, 6) + "********" + value.substring(14) : "***";
    }

    private void requirePermission(SysUser user, Long projectId, String permission) {
        if (user == null || projectId == null) throw BusinessException.of(403, "无权访问常用资料");
        permissionService.requireSystemPermission(user.getId(), projectId, permission);
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim()
                : Objects.toString(user.getUsername(), "系统用户");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int versionOf(SiteVisitorProfile profile) {
        return profile.getVersion() == null ? 0 : profile.getVersion();
    }

    private void requireSingle(int affected, String action) {
        if (affected != 1) throw stateConflict(action + "状态已变化，请重试");
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message);
    }

    public record PersonData(String personType, String personName, String idCard) {
    }

    public record SubmissionData(String visitorCompany, String contactName, String contactPhone,
                                 String travelMode, String vehiclePlate, List<PersonData> people) {
    }
}
