package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicMeetingCheckinMeetingVO {
    private String inviteNo;
    private String projectName;
    private String projectShortName;
    private String purpose;
    private String visitLocation;
    private String hostName;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private LocalDateTime checkinStartTime;
    private LocalDateTime checkinEndTime;
    private Integer locationRadiusMeters;
    private Boolean projectLocationAvailable;
    private LocalDateTime serverTime;
}
