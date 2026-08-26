package com.example.siteplatform.inspection.general.vo;

import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GeneralInspectionPlanVO {
    private Long id;
    private Long projectId;
    private Long templateId;
    private String planCode;
    private String planName;
    private String status;
    private GeneralInspectionPlanConfig config;
    private Long currentVersionId;
    private Integer currentVersionNo;
    private LocalDateTime effectiveTime;
    private LocalDateTime generatedThroughTime;
    private Integer version;
}
