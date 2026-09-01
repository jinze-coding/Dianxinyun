package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SiteMeetingVisitRegistrationVO {
    private Long id;
    private String registrationNo;
    private Long invitationId;
    private String inviteNo;
    private Long projectId;
    private String projectName;
    private String meetingTopic;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private String status;
    private String visitorCompany;
    private String contactName;
    private String contactPhone;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String visitorRemark;
    private String sourceProfileName;
    private LocalDateTime registeredTime;
    private String voidReason;
    private String voidedByName;
    private LocalDateTime voidedTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SiteGuardVisitPersonVO> visitors;
    private List<SiteGuardVisitAuditVO> auditLogs;
}
