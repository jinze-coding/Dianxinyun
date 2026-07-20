package com.example.siteplatform.electricbox.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ElectricBoxScopeVO {
    private Long id;
    private Long projectId;
    private Long electricBoxId;
    private Boolean included;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String reason;
    private String operatorName;
    private Boolean effectiveToday;
}
