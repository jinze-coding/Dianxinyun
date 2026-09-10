package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMeetingAttendeeVO {
    private Long personId;
    private Long registrationId;
    private String registrationNo;
    private String registrationSource;
    private String registrationStatus;
    private String personType;
    private String personCompany;
    private String personName;
    private String personPhone;
    private Integer sortOrder;
    private LocalDateTime registeredTime;
    private String travelMode;
    private String vehiclePlate;
    private String attendanceStatus;
    private String checkinMethod;
    private LocalDateTime checkinTime;
    private String locationResult;
    private Integer distanceMeters;
    private Integer accuracyMeters;
    private String revokedByName;
    private LocalDateTime revokedTime;
    private String revokeReason;
    private Integer version;
}
