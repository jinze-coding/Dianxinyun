package com.example.siteplatform.siteaccess.service;

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
    private final VisitorSessionService sessions;
    private final VisitorDataCryptoService crypto;
    private final ObjectMapper json;

    public VisitorPersonalProfileService(SiteVisitorPersonalProfileMapper mapper, ProjectInfoMapper projects,
            VisitorSessionService sessions,
            VisitorDataCryptoService crypto, ObjectMapper json) {
        this.mapper = mapper;
        this.projects = projects;
        this.sessions = sessions;
        this.crypto = crypto;
        this.json = json;
    }

    public VisitorPersonalInfoVO read(VisitorSessionService.VisitorSessionContext context) {
        if (context == null) throw BusinessException.of(401, "请重新获取微信身份后读取");
        var profile = mapper.selectLatestPersonalInfo(context.appId(), identity(context),
                VisitorIdentitySupport.hash("single-registration", context, crypto, sessions),
                VisitorIdentitySupport.hash("meeting-registration", context, crypto, sessions),
                VisitorIdentitySupport.hash("guard-registration", context, crypto, sessions));
        if (profile == null) profile = mapper.selectLatestNamedPersonalInfo(context.appId(), context.identityHash());
        var result = new VisitorPersonalInfoVO();
        // 保留旧响应字段，但保存规则统一为成功提交后自动保存，不由客户端开关决定。
        result.setRememberInfo(true);
        if (profile != null) {
            result.setAvailable(true);
            result.setVisitorCompany(profile.getVisitorCompany());
            result.setContactName(profile.getContactName());
            result.setContactPhone(crypto.decrypt(profile.getContactPhoneEncrypted()));
            result.setTravelMode(profile.getTravelMode());
            result.setVehiclePlate(profile.getVehiclePlate());
        }
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOnSubmission(VisitorSessionService.VisitorSessionContext context, Boolean rememberInfo,
                                 VisitorSubmissionNormalizer.Submission submission) {
        if (context == null) throw BusinessException.of(401, "请重新获取微信身份后提交");
        if (projects.selectByIdForUpdate(context.projectId()) == null) throw BusinessException.notFound("项目不存在");
        var profile = mapper.selectOwnerForUpdate(context.projectId(), context.appId(), identity(context));
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
        profile.setRememberEnabled(true);
        profile.setVisitorCompany(submission.visitorCompany());
        profile.setContactName(submission.contactName());
        profile.setContactPhoneEncrypted(crypto.encrypt(submission.contactPhone()));
        profile.setTravelMode(submission.travelMode());
        profile.setVehiclePlate(submission.vehiclePlate());
        if (profile.getPrivacyAgreedTime() == null) profile.setPrivacyAgreedTime(now);
        profile.setLastSubmittedTime(now);
        profile.setUpdateTime(now);
        requireSingle(creating ? mapper.insert(profile) : mapper.updateById(profile));
        requireSingle(mapper.insertAudit(profile.getId(), profile.getProjectId(),
                "AUTO_SAVE", before, snapshot(profile)));
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
