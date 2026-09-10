package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.siteaccess.entity.SiteMeetingCheckinQr;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingCheckinQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingCheckinQrProvisionerTest {
    @Mock private SiteMeetingCheckinQrMapper qrMapper;
    @Mock private SiteMeetingVisitAuditLogMapper auditMapper;
    @Mock private VisitorDataCryptoService cryptoService;

    @Test
    void defaultCheckinWindowFollowsMeetingTimeChangeAtomically() {
        MeetingCheckinQrProvisioner provisioner = provisioner();
        LocalDateTime oldStart = LocalDateTime.of(2026, 9, 2, 10, 0);
        LocalDateTime oldEnd = LocalDateTime.of(2026, 9, 2, 12, 0);
        SiteVisitInvitation invitation = invitation(LocalDateTime.of(2026, 9, 2, 14, 0),
                LocalDateTime.of(2026, 9, 2, 16, 0));
        SiteMeetingCheckinQr qr = currentQr(oldStart.minusHours(1), oldEnd);
        when(qrMapper.selectCurrentForUpdate(11L)).thenReturn(qr);
        when(qrMapper.updateById(qr)).thenReturn(1);
        when(cryptoService.encrypt(any(String.class))).thenReturn("encrypted-audit");
        when(auditMapper.insert(any())).thenReturn(1);

        provisioner.reconcileMeetingTimeChange(invitation, oldStart, oldEnd, operator());

        assertThat(qr.getCheckinStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 13, 0));
        assertThat(qr.getCheckinEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 16, 0));
        assertThat(qr.getVersion()).isEqualTo(4);
        ArgumentCaptor<SiteMeetingVisitAuditLog> audit = ArgumentCaptor.forClass(SiteMeetingVisitAuditLog.class);
        verify(auditMapper).insert(audit.capture());
        assertThat(audit.getValue().getActionType()).isEqualTo("CHECKIN_QR_MEETING_SYNC");
    }

    @Test
    void customWindowIsClampedWhenNewMeetingEndMovesEarlier() {
        MeetingCheckinQrProvisioner provisioner = provisioner();
        LocalDateTime oldStart = LocalDateTime.of(2026, 9, 2, 10, 0);
        LocalDateTime oldEnd = LocalDateTime.of(2026, 9, 2, 18, 0);
        SiteVisitInvitation invitation = invitation(LocalDateTime.of(2026, 9, 2, 12, 0),
                LocalDateTime.of(2026, 9, 2, 15, 0));
        SiteMeetingCheckinQr qr = currentQr(LocalDateTime.of(2026, 9, 2, 9, 30),
                LocalDateTime.of(2026, 9, 2, 17, 0));
        when(qrMapper.selectCurrentForUpdate(11L)).thenReturn(qr);
        when(qrMapper.updateById(qr)).thenReturn(1);
        when(cryptoService.encrypt(any(String.class))).thenReturn("encrypted-audit");
        when(auditMapper.insert(any())).thenReturn(1);

        provisioner.reconcileMeetingTimeChange(invitation, oldStart, oldEnd, operator());

        assertThat(qr.getCheckinStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 9, 30));
        assertThat(qr.getCheckinEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 15, 0));
    }

    @Test
    void voidedMeetingCannotLazilyCreateVenueQr() {
        MeetingCheckinQrProvisioner provisioner = provisioner();
        SiteVisitInvitation invitation = invitation(LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2));
        invitation.setStatus(SiteAccessService.STATUS_VOIDED);

        assertThatThrownBy(() -> provisioner.provision(invitation, operator()))
                .isInstanceOfSatisfying(com.example.siteplatform.common.BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(409));
        verify(qrMapper, never()).insert(any());
    }

    private MeetingCheckinQrProvisioner provisioner() {
        return new MeetingCheckinQrProvisioner(qrMapper, auditMapper, cryptoService,
                new ObjectMapper().findAndRegisterModules());
    }

    private SiteVisitInvitation invitation(LocalDateTime start, LocalDateTime end) {
        SiteVisitInvitation invitation = new SiteVisitInvitation();
        invitation.setId(11L);
        invitation.setProjectId(7L);
        invitation.setInviteType(SiteAccessService.INVITE_TYPE_MEETING);
        invitation.setStatus(SiteAccessService.STATUS_OPEN);
        invitation.setVisitStartTime(start);
        invitation.setVisitEndTime(end);
        return invitation;
    }

    private SiteMeetingCheckinQr currentQr(LocalDateTime start, LocalDateTime end) {
        SiteMeetingCheckinQr qr = new SiteMeetingCheckinQr();
        qr.setId(31L);
        qr.setInvitationId(11L);
        qr.setProjectId(7L);
        qr.setQrStatus(MeetingCheckinService.QR_ENABLED);
        qr.setQrVersion(1);
        qr.setCheckinStartTime(start);
        qr.setCheckinEndTime(end);
        qr.setLocationRadiusMeters(300);
        qr.setVersion(3);
        return qr;
    }

    private SysUser operator() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("manager");
        return user;
    }
}
