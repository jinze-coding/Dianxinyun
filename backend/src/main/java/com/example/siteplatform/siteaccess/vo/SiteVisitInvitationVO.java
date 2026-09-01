package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SiteVisitInvitationVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String inviteNo;
    private String inviteType;
    private String status;
    private Long registrationGroupCount;
    private Long registeredPersonCount;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private String purpose;
    private String visitLocation;
    private Long hostUserId;
    private String hostName;
    private String hostPhone;
    private String internalRemark;
    private String visitorCompany;
    private String contactName;
    private String contactPhone;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String visitorRemark;
    private Long sourceProfileId;
    private String sourceProfileName;
    private LocalDateTime submittedTime;
    private String voidReason;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SiteVisitPersonVO> visitors = new ArrayList<>();
    private List<SiteVisitAuditVO> auditLogs = new ArrayList<>();
}
