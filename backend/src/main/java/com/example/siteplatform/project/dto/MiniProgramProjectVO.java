package com.example.siteplatform.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MiniProgramProjectVO {
    private Long id;
    private String projectName;
    private String shortName;
    private String area;
    private String period;
    private String phase;
    private String projectStatus;
    private String safetyGoal;
    private String qualityGoal;
    private String address;
    private String manager;
    private String contractor;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String province;
    private String city;
    private String district;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateType;
    private String status;
    private String stage;
    private Integer electricBoxTotal;
    private Integer pendingTodoCount;
    private Integer todayInspectionCount;
    private Integer pendingReviewCount;
    private Integer pendingRectificationCount;
}
