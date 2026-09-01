package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PublicMeetingVisitPassVO {
    private String registrationNo;
    private String status;
    private String inviteNo;
    private String projectName;
    private String projectShortName;
    private String purpose;
    private String visitLocation;
    private String hostName;
    private String visitorCompany;
    private String contactName;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private LocalDateTime visitStartTime;
    private LocalDateTime validUntil;
    private LocalDateTime registeredTime;
    private LocalDateTime serverTime;
    private List<SiteGuardVisitPersonVO> visitors;
    private PublicProjectLocationVO projectLocation;
}
