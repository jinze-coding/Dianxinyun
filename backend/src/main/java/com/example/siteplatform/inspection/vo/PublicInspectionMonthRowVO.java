package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PublicInspectionMonthRowVO {
    private LocalDate date;
    private Boolean required;
    private String status;
    private String appearance;
    private String leakageProtector;
    private String fuse;
    private String protectiveZero;
    private String socket220v;
    private String socket380v;
    private String inspectorName;
    private String remark;
}
