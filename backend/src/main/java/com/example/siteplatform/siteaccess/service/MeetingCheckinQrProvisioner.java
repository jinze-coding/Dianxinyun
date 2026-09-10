package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.siteaccess.entity.SiteMeetingCheckinQr;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingCheckinQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class MeetingCheckinQrProvisioner {
    private static final int DEFAULT_RADIUS_METERS = 300;
    private final SiteMeetingCheckinQrMapper qrMapper;
    private final SiteMeetingVisitAuditLogMapper auditMapper;
    private final VisitorDataCryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public MeetingCheckinQrProvisioner(SiteMeetingCheckinQrMapper qrMapper,
                                       SiteMeetingVisitAuditLogMapper auditMapper,
                                       VisitorDataCryptoService cryptoService,
                                       ObjectMapper objectMapper) {
        this.qrMapper = qrMapper;
        this.auditMapper = auditMapper;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    public SiteMeetingCheckinQr provision(SiteVisitInvitation invitation, SysUser operator) {
        if (invitation == null || !SiteAccessService.INVITE_TYPE_MEETING.equals(invitation.getInviteType())) {
            throw new BusinessException("只有会议邀请可以创建签到码");
        }
        if (SiteAccessService.STATUS_VOIDED.equals(invitation.getStatus())) {
            throw BusinessException.of(409, "已作废会议不能创建签到码");
        }
        SiteMeetingCheckinQr existing = qrMapper.selectCurrentForUpdate(invitation.getId());
        if (existing != null) return existing;
        return create(invitation, operator, 1,
                invitation.getVisitStartTime().minusHours(1), invitation.getVisitEndTime(),
                DEFAULT_RADIUS_METERS, "CHECKIN_QR_CREATE", "创建会议现场签到码");
    }

    public SiteMeetingCheckinQr provisionReplacement(SiteVisitInvitation invitation,
                                                      SiteMeetingCheckinQr previous,
                                                      SysUser operator) {
        SiteMeetingCheckinQr existing = qrMapper.selectCurrentForUpdate(invitation.getId());
        if (existing != null) throw BusinessException.of(409, "会议签到码轮换状态已变化，请刷新后重试");
        return create(invitation, operator, previous.getQrVersion() + 1,
                previous.getCheckinStartTime(), previous.getCheckinEndTime(),
                previous.getLocationRadiusMeters(), "CHECKIN_QR_ROTATE", "轮换会议现场签到码");
    }

    public void reconcileMeetingTimeChange(SiteVisitInvitation invitation,
                                           LocalDateTime previousStartTime,
                                           LocalDateTime previousEndTime,
                                           SysUser operator) {
        SiteMeetingCheckinQr qr = qrMapper.selectCurrentForUpdate(invitation.getId());
        if (qr == null || previousStartTime == null || previousEndTime == null) return;
        LocalDateTime defaultStart = invitation.getVisitStartTime().minusHours(1);
        LocalDateTime earliestStart = invitation.getVisitStartTime().minusDays(1);
        LocalDateTime nextStart = Objects.equals(qr.getCheckinStartTime(), previousStartTime.minusHours(1))
                ? defaultStart : qr.getCheckinStartTime();
        LocalDateTime nextEnd = Objects.equals(qr.getCheckinEndTime(), previousEndTime)
                ? invitation.getVisitEndTime() : min(qr.getCheckinEndTime(), invitation.getVisitEndTime());
        if (nextStart.isBefore(earliestStart) || !nextStart.isBefore(nextEnd)) {
            nextStart = defaultStart;
            nextEnd = invitation.getVisitEndTime();
        }
        if (Objects.equals(qr.getCheckinStartTime(), nextStart)
                && Objects.equals(qr.getCheckinEndTime(), nextEnd)) return;
        Map<String, Object> before = snapshot(qr);
        qr.setCheckinStartTime(nextStart);
        qr.setCheckinEndTime(nextEnd);
        qr.setUpdatedById(operator.getId());
        qr.setUpdatedByName(displayName(operator));
        qr.setVersion((qr.getVersion() == null ? 0 : qr.getVersion()) + 1);
        qr.setUpdateTime(LocalDateTime.now());
        requireSingle(qrMapper.updateById(qr), "会议时间同步签到窗口");
        writeAudit(invitation, qr, operator, "CHECKIN_QR_MEETING_SYNC", "会议时间变化，同步签到窗口");
    }

    private SiteMeetingCheckinQr create(SiteVisitInvitation invitation, SysUser operator, int qrVersion,
                                         LocalDateTime checkinStartTime, LocalDateTime checkinEndTime,
                                         int locationRadiusMeters, String auditAction, String auditComment) {
        LocalDateTime now = LocalDateTime.now();
        String rawToken = randomToken();
        SiteMeetingCheckinQr qr = new SiteMeetingCheckinQr();
        qr.setInvitationId(invitation.getId());
        qr.setProjectId(invitation.getProjectId());
        qr.setSceneTokenHash(cryptoService.digest(rawToken));
        qr.setSceneTokenEncrypted(cryptoService.encrypt(rawToken));
        qr.setQrStatus(MeetingCheckinService.QR_ENABLED);
        qr.setQrVersion(qrVersion);
        qr.setCheckinStartTime(checkinStartTime);
        qr.setCheckinEndTime(checkinEndTime);
        qr.setLocationRadiusMeters(locationRadiusMeters);
        qr.setCreatedById(operator.getId());
        qr.setCreatedByName(displayName(operator));
        qr.setUpdatedById(operator.getId());
        qr.setUpdatedByName(displayName(operator));
        qr.setVersion(0);
        qr.setDeleted(0);
        qr.setCreateTime(now);
        qr.setUpdateTime(now);
        try {
            requireSingle(qrMapper.insert(qr), "会议签到码创建");
        } catch (DuplicateKeyException duplicate) {
            SiteMeetingCheckinQr raced = qrMapper.selectCurrentForUpdate(invitation.getId());
            if (raced != null) return raced;
            throw BusinessException.of(409, "会议签到码创建冲突，请重试");
        }
        writeAudit(invitation, qr, operator, auditAction, auditComment);
        return qr;
    }

    private void writeAudit(SiteVisitInvitation invitation, SiteMeetingCheckinQr qr, SysUser operator,
                            String action, String comment) {
        SiteMeetingVisitAuditLog audit = new SiteMeetingVisitAuditLog();
        audit.setInvitationId(invitation.getId());
        audit.setCheckinQrId(qr.getId());
        audit.setProjectId(invitation.getProjectId());
        audit.setActionType(action);
        audit.setOperatorId(operator.getId());
        audit.setOperatorName(displayName(operator));
        audit.setAfterSnapshotEncrypted(encrypt(snapshot(qr)));
        audit.setComment(comment);
        audit.setCreateTime(LocalDateTime.now());
        requireSingle(auditMapper.insert(audit), "会议签到码审计写入");
    }

    private Map<String, Object> snapshot(SiteMeetingCheckinQr qr) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qrStatus", qr.getQrStatus());
        result.put("qrVersion", qr.getQrVersion());
        result.put("checkinStartTime", qr.getCheckinStartTime());
        result.put("checkinEndTime", qr.getCheckinEndTime());
        result.put("locationRadiusMeters", qr.getLocationRadiusMeters());
        result.put("version", qr.getVersion());
        return result;
    }

    private String encrypt(Map<String, Object> snapshot) {
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会议签到码审计快照生成失败", exception);
        }
    }

    private String randomToken() {
        byte[] value = new byte[16];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? second : first;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim()
                : Objects.toString(user.getUsername(), "-");
    }

    private void requireSingle(int affected, String action) {
        if (affected != 1) throw BusinessException.of(409, action + "状态已变化，请刷新后重试");
    }
}
