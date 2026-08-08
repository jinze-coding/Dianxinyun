package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicSiteVisitInvitationVO {
    private String inviteNo;
    private String status;
    private String projectName;
    private String projectShortName;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private String purpose;
    private String visitLocation;
    private String hostName;
    private String hostPhone;
}
