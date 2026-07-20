package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.util.List;

@Data
public class PublicElectricBoxMonthlyVO {
    private String projectName;
    private String projectShortName;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String status;
    private String month;
    private Integer shouldCheckDays;
    private Integer checkedDays;
    private Integer missedDays;
    private Integer abnormalDays;
    private Integer openRectificationCount;
    private List<PublicInspectionMonthRowVO> rows;
}
