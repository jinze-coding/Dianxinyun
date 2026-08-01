package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.util.List;

@Data
public class InspectionMonthSummaryVO {
    private Long projectId;
    private Long electricBoxId;
    private String month;
    private String periodType;
    private String periodValue;
    private Integer shouldCheck;
    private Integer checked;
    private Integer missed;
    private Integer abnormal;
    private Integer openRectification;
    private List<InspectionRecordVO> records;
}
