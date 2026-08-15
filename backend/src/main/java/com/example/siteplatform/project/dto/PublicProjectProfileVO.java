package com.example.siteplatform.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PublicProjectProfileVO {
    private String projectName;
    private String shortName;
    private String phase;
    private String address;
    private String engineeringType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private String description;
    private String directCompany;
    private String ownerUnit;
    private String supervisionUnit;
    private String designUnit;
    private String contractor;
    private BigDecimal buildingArea;
    private BigDecimal landArea;
    private BigDecimal buildingHeight;
    private BigDecimal excavationDepth;
    private Integer undergroundFloorCount;
    private Integer abovegroundFloorCount;
    private String projectScale;
    private String projectClassification;
    private String projectLevel;
    private String projectTarget;
    private String qualityGoal;
    private String safetyGoal;
    private String greenConstructionGoal;
    private List<PublicProjectProfileImageVO> images = new ArrayList<>();
}
