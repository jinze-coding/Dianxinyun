package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteMeetingAttendanceSummaryVO {
    private Long reservedPersonCount;
    private Long reservedCheckedInCount;
    private Long reservedPendingCount;
    private Long walkInCheckedInCount;
    private Long totalCheckedInCount;
    private Double reservedAttendanceRate;
    private Long inRangeCount;
    private Long outOfRangeCount;
    private Long unavailableLocationCount;
    private Long noReferenceLocationCount;
    private Long manualLocationCount;
    private SiteMeetingCheckinSettingsVO settings;
}
