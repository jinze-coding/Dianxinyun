package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionScanVO {
    private String mode;
    private String publicCode;
    private Long projectId;
    private Long pointId;
    private String pointCode;
    private String pointName;
    private String locationDesc;
    private String reason;
    private Boolean publicAccessEnabled;
    private List<GeneralInspectionTaskVO> eligibleTasks;
}
