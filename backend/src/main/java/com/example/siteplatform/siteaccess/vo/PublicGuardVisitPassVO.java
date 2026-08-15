package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicGuardVisitPassVO {
    private String registrationNo;
    private String status;
    private String projectName;
    private String projectShortName;
    private String visitorCompany;
    private String contactName;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private LocalDateTime registeredTime;
    private LocalDateTime validUntil;
    private LocalDateTime serverTime;
}
