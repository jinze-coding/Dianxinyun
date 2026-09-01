package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class PublicMeetingVisitorSessionVO {
    private String visitorSessionToken;
    private long expiresInSeconds;
    private String pageState;
    private PublicSiteVisitInvitationVO invitation;
    private PublicMeetingVisitPassVO registration;
}
