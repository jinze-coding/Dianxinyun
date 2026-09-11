package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuardMeetingChoiceServiceTest {
    private final SiteVisitInvitationMapper invitations = mock(SiteVisitInvitationMapper.class);
    private final SiteMeetingVisitRegistrationMapper registrations = mock(SiteMeetingVisitRegistrationMapper.class);
    private final VisitorSessionService sessions = mock(VisitorSessionService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> stored = new HashMap<>();
    private final VisitorSessionService.VisitorSessionContext context = context(7L);
    private GuardMeetingChoiceService service;

    @BeforeEach
    void setup() {
        var env = new MockEnvironment();
        env.setActiveProfiles("test");
        var crypto = new VisitorDataCryptoService("", env);
        service = new GuardMeetingChoiceService(invitations, registrations, sessions, crypto, redis,
                new ObjectMapper().findAndRegisterModules());
        when(sessions.decryptOpenid(any())).thenReturn("synthetic-openid");
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
        when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    }

    @Test
    void pollingKeepsUnchangedChoicesAndDoesNotAccumulateRedisKeys() {
        when(invitations.selectList(any())).thenReturn(List.of(meeting(11L, 2)));
        var first = service.list(context, "synthetic-session").get(0);
        for (int i = 0; i < 40; i++) {
            assertThat(service.list(context, "synthetic-session").get(0).choiceToken()).isEqualTo(first.choiceToken());
        }
        assertThat(first.choiceToken()).matches("[a-f0-9]{32}");
        assertThat(stored).hasSize(1);
        assertThat(service.resolve(List.of(first.choiceToken(), first.choiceToken()), context, "synthetic-session"))
                .singleElement().satisfies(choice -> {
                    assertThat(choice.invitationId()).isEqualTo(11L);
                    assertThat(choice.version()).isEqualTo(2);
                });
    }

    @Test
    void everyRefreshUsesCurrentMeetingsAndVersions() {
        when(invitations.selectList(any())).thenReturn(List.of(meeting(11L, 2)),
                List.of(meeting(11L, 3), meeting(12L, 0)), List.of(meeting(12L, 0)));
        var first = service.list(context, "synthetic-session");
        var changed = service.list(context, "synthetic-session");
        var removed = service.list(context, "synthetic-session");
        assertThat(changed).hasSize(2);
        assertThat(changed.get(0).choiceToken()).isNotEqualTo(first.get(0).choiceToken());
        assertThat(removed).singleElement().isEqualTo(changed.get(1));
        assertThat(service.resolve(List.of(first.get(0).choiceToken()), context, "synthetic-session"))
                .singleElement().satisfies(choice -> assertThat(choice.version()).isEqualTo(2));
        // 旧凭证保留旧版本，由登记事务拒绝；不能在刷新时静默升级已选择的会议版本。
        assertThat(service.resolve(List.of(changed.get(0).choiceToken()), context, "synthetic-session"))
                .singleElement().satisfies(choice -> assertThat(choice.version()).isEqualTo(3));
        verify(invitations, times(3)).selectList(any());
    }

    @Test
    void choicesRemainIsolatedBySessionAndProject() {
        when(invitations.selectList(any())).thenReturn(List.of(meeting(11L, 2)));
        var first = service.list(context, "session-a").get(0).choiceToken();
        assertThat(service.list(context, "session-b").get(0).choiceToken()).isNotEqualTo(first);
        assertThat(service.list(context(8L), "session-a").get(0).choiceToken()).isNotEqualTo(first);
        for (var token : List.of("session-b", "expired-session")) {
            assertThatThrownBy(() -> service.resolve(List.of(first), context, token))
                    .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
        }
        assertThatThrownBy(() -> service.resolve(List.of(first), context(8L), "session-a"))
                .isInstanceOf(BusinessException.class);
        stored.clear();
        assertThatThrownBy(() -> service.resolve(List.of(first), context, "session-a"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void registrationStatusRefreshesWithoutChangingTheMeetingChoice() {
        when(invitations.selectList(any())).thenReturn(List.of(meeting(11L, 2)));
        var registration = new SiteMeetingVisitRegistration();
        registration.setInvitationId(11L);
        when(registrations.selectList(any())).thenReturn(List.of(), List.of(registration));
        var first = service.list(context, "session-a").get(0);
        var next = service.list(context, "session-a").get(0);
        assertThat(first.registered()).isFalse();
        assertThat(next.registered()).isTrue();
        assertThat(next.choiceToken()).isEqualTo(first.choiceToken());
    }

    private static VisitorSessionService.VisitorSessionContext context(Long projectId) {
        return new VisitorSessionService.VisitorSessionContext(null, projectId, "test-app", "owner-hash",
                "encrypted-synthetic-openid", VisitorSessionService.SOURCE_GUARD_QR, 5L);
    }

    private static SiteVisitInvitation meeting(Long id, int version) {
        var row = new SiteVisitInvitation();
        row.setId(id);
        row.setProjectId(7L);
        row.setVersion(version);
        row.setPurpose("测试会议 " + id);
        row.setVisitStartTime(LocalDateTime.of(2026, 9, 11, 9, 0));
        row.setVisitEndTime(LocalDateTime.of(2026, 9, 11, 18, 0));
        row.setVisitLocation("测试会议室");
        return row;
    }
}
