package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PublicElectricBoxSummaryVO {
    private String projectShortName;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String status;
    private LocalDate rangeStartDate;
    private LocalDate rangeEndDate;
    private LocalDate latestCheckDate;
    private Integer shouldCheckDays;
    private Integer checkedDays;
    private Integer missedDays;
    private Integer abnormalCount;
    private Integer openRectificationCount;
    private List<PublicInspectionRecordVO> recentRecords;
}
