package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorPersonalProfile;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorPersonalProfileMapper;
import com.example.siteplatform.siteaccess.vo.VisitorPersonalInfoVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class VisitorPersonalProfileService {
    private final SiteVisitorPersonalProfileMapper mapper;
    private final ProjectInfoMapper projects;
    private final VisitorProfileService namedProfiles;
    private final VisitorSessionService sessions;
    private final VisitorDataCryptoService crypto;
    private final ObjectMapper json;

    public VisitorPersonalProfileService(SiteVisitorPersonalProfileMapper mapper, ProjectInfoMapper projects,
            VisitorProfileService namedProfiles, VisitorSessionService sessions,
            VisitorDataCryptoService crypto, ObjectMapper json) {
        this.mapper = mapper;
        this.projects = projects;
        this.namedProfiles = namedProfiles;
        this.sessions = sessions;
        this.crypto = crypto;
        this.json = json;
    }

    public VisitorPersonalInfoVO read(VisitorSessionService.VisitorSessionContext context) {
        var profile = mapper.selectOne(new LambdaQueryWrapper<SiteVisitorPersonalProfile>()
                .eq(SiteVisitorPersonalProfile::getProjectId, context.projectId())
                .eq(SiteVisitorPersonalProfile::getWechatAppId, context.appId())
                .eq(SiteVisitorPersonalProfile::getOwnerIdentityHash, identity(context)));
        var result = new VisitorPersonalInfoVO();
        if (profile != null) {
            result.setRememberInfo(Boolean.TRUE.equals(profile.getRememberEnabled()));
            if (result.isRememberInfo()) {
                result.setAvailable(true);
                result.setVisitorCompany(profile.getVisitorCompany());
                result.setContactName(profile.getContactName());
                result.setContactPhone(crypto.decrypt(profile.getContactPhoneEncrypted()));
                result.setTravelMode(profile.getTravelMode());
                result.setVehiclePlate(profile.getVehiclePlate());
            }
            // A disabled row is also an explicit opt-out from legacy fallback.
            return result;
        }
        var saved = namedProfiles.publicList(context);
        if (!saved.isEmpty()) {
            var detail = namedProfiles.publicDetail(context, saved.get(0).getProfileCode());
            result.setAvailable(true);
            result.setVisitorCompany(detail.getVisitorCompany());
            result.setContactName(detail.getContactName());
            result.setContactPhone(detail.getContactPhone());
            result.setTravelMode(detail.getTravelMode());
            result.setVehiclePlate(detail.getVehiclePlate());
        }
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOnSubmission(VisitorSessionService.VisitorSessionContext context, Boolean rememberInfo,
                                 VisitorSubmissionNormalizer.Submission submission) {
        if (context == null) throw BusinessException.of(401, "请重新获取微信身份后提交");
        if (projects.selectByIdForUpdate(context.projectId()) == null) throw BusinessException.notFound("项目不存在");
        var profile = mapper.selectOwnerForUpdate(context.projectId(), context.appId(), identity(context));
        if (rememberInfo == null && (profile == null || !Boolean.TRUE.equals(profile.getRememberEnabled()))) return;
        boolean remember = rememberInfo == null || rememberInfo;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String before = profile == null ? null : snapshot(profile);
        boolean creating = profile == null;
        if (creating) {
            profile = new SiteVisitorPersonalProfile();
            profile.setProjectId(context.projectId());
            profile.setWechatAppId(context.appId());
            profile.setOwnerIdentityHash(identity(context));
            profile.setVersion(0);
            profile.setDeleted(0);
            profile.setCreateTime(now);
        } else {
            profile.setVersion(profile.getVersion() + 1);
        }
        profile.setRememberEnabled(remember);
        profile.setVisitorCompany(remember ? submission.visitorCompany() : null);
        profile.setContactName(remember ? submission.contactName() : null);
        profile.setContactPhoneEncrypted(remember ? crypto.encrypt(submission.contactPhone()) : null);
        profile.setTravelMode(remember ? submission.travelMode() : null);
        profile.setVehiclePlate(remember ? submission.vehiclePlate() : null);
        if (remember && profile.getPrivacyAgreedTime() == null) profile.setPrivacyAgreedTime(now);
        profile.setLastSubmittedTime(now);
        profile.setUpdateTime(now);
        requireSingle(creating ? mapper.insert(profile) : mapper.updateById(profile));
        requireSingle(mapper.insertAudit(profile.getId(), profile.getProjectId(),
                remember ? "REMEMBER" : "FORGET", before, snapshot(profile)));
    }

    private String identity(VisitorSessionService.VisitorSessionContext context) {
        return VisitorIdentitySupport.hash("personal-profile", context, crypto, sessions);
    }

    private String snapshot(SiteVisitorPersonalProfile profile) {
        try {
            return crypto.encrypt(json.writeValueAsString(profile));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("本人快捷资料审计生成失败", exception);
        }
    }

    private void requireSingle(int count) {
        if (count != 1) throw BusinessException.of(409, "本人快捷资料写入冲突，请重试");
    }
}
