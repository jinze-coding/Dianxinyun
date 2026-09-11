package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.vo.PublicGuardMeetingChoiceVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class GuardMeetingChoiceService {
    private static final String PREFIX = "site-access:guard-meeting-choice:";
    private final SiteVisitInvitationMapper invitations;
    private final SiteMeetingVisitRegistrationMapper registrations;
    private final VisitorSessionService sessions;
    private final VisitorDataCryptoService crypto;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public GuardMeetingChoiceService(SiteVisitInvitationMapper invitations, SiteMeetingVisitRegistrationMapper registrations,
            VisitorSessionService sessions, VisitorDataCryptoService crypto, StringRedisTemplate redis, ObjectMapper json) {
        this.invitations = invitations;
        this.registrations = registrations;
        this.sessions = sessions;
        this.crypto = crypto;
        this.redis = redis;
        this.json = json;
    }

    public List<PublicGuardMeetingChoiceVO> list(VisitorSessionService.VisitorSessionContext context, String sessionToken) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        var values = invitations.selectList(new LambdaQueryWrapper<SiteVisitInvitation>()
                .eq(SiteVisitInvitation::getProjectId, context.projectId())
                .eq(SiteVisitInvitation::getInviteType, "MEETING").eq(SiteVisitInvitation::getStatus, "OPEN")
                .gt(SiteVisitInvitation::getVisitEndTime, now)
                .lt(SiteVisitInvitation::getVisitStartTime, now.toLocalDate().plusDays(7).atStartOfDay())
                .orderByAsc(SiteVisitInvitation::getVisitStartTime).orderByAsc(SiteVisitInvitation::getId));
        Set<Long> registered = new HashSet<>();
        if (!values.isEmpty()) {
            registrations.selectList(new LambdaQueryWrapper<SiteMeetingVisitRegistration>()
                    .eq(SiteMeetingVisitRegistration::getProjectId, context.projectId())
                    .eq(SiteMeetingVisitRegistration::getWechatAppId, context.appId())
                    .eq(SiteMeetingVisitRegistration::getVisitorIdentityHash,
                            VisitorIdentitySupport.hash("meeting-registration", context, crypto, sessions))
                    .eq(SiteMeetingVisitRegistration::getStatus, "REGISTERED")
                    .in(SiteMeetingVisitRegistration::getInvitationId, values.stream().map(SiteVisitInvitation::getId).toList()))
                    .forEach(row -> registered.add(row.getInvitationId()));
        }
        var result = new ArrayList<PublicGuardMeetingChoiceVO>();
        for (var value : values) {
            // 同一短会话、同一会议版本保持稳定，轮询不会丢失访客已勾选的会议。
            // 版本或会话变化即换证；仍通过 Redis 校验归属和有效期，不暴露业务主键。
            String token = crypto.fingerprint("guard-meeting-choice:v1", crypto.digest(sessionToken)
                    + ":" + context.projectId() + ":" + value.getId() + ":" + value.getVersion()).substring(0, 32);
            var choice = new Choice(value.getId(), context.projectId(), value.getVersion(), crypto.digest(sessionToken));
            try {
                redis.opsForValue().set(PREFIX + crypto.digest(token), json.writeValueAsString(choice), Duration.ofMinutes(30));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("会议选择凭证生成失败", exception);
            }
            result.add(new PublicGuardMeetingChoiceVO(token, value.getPurpose(), value.getVisitStartTime(),
                    value.getVisitEndTime(), value.getVisitLocation(), registered.contains(value.getId())));
        }
        return result;
    }

    public List<Choice> resolve(List<String> tokens, VisitorSessionService.VisitorSessionContext context, String sessionToken) {
        if (tokens == null || tokens.isEmpty()) return List.of();
        if (tokens.size() > 50) throw new BusinessException("一次最多选择50场会议");
        Map<Long, Choice> choices = new TreeMap<>();
        for (String token : new LinkedHashSet<>(tokens)) {
            if (token == null || !token.matches("[a-f0-9]{32}")) throw invalid();
            String stored = redis.opsForValue().get(PREFIX + crypto.digest(token));
            if (stored == null) throw invalid();
            try {
                var choice = json.readValue(stored, Choice.class);
                if (!Objects.equals(choice.projectId(), context.projectId())
                        || !Objects.equals(choice.sessionHash(), crypto.digest(sessionToken))) throw invalid();
                var previous = choices.putIfAbsent(choice.invitationId(), choice);
                if (previous != null && !Objects.equals(previous.version(), choice.version())) throw invalid();
            } catch (JsonProcessingException exception) {
                throw invalid();
            }
        }
        return new ArrayList<>(choices.values());
    }

    private BusinessException invalid() {
        return BusinessException.of(409, "会议选择已失效，请刷新列表后重新选择");
    }

    public record Choice(Long invitationId, Long projectId, Integer version, String sessionHash) {}
}
