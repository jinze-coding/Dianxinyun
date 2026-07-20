package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PublicInspectionRecordVO {
    private LocalDate checkDate;
    private LocalDateTime inspectedAt;
    private String source;
    private String status;
    private Integer abnormalCount;
}
