package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMeetingCheckinSettingsVO {
    private Long id;
    private Long invitationId;
    private Long projectId;
    private String qrStatus;
    private String effectiveStatus;
    private Integer qrVersion;
    private LocalDateTime checkinStartTime;
    private LocalDateTime checkinEndTime;
    private Integer locationRadiusMeters;
    private Boolean projectLocationAvailable;
    private Integer version;
    private LocalDateTime serverTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
