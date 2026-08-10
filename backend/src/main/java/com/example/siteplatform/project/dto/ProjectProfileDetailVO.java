package com.example.siteplatform.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectProfileDetailVO {
    private Long projectId;
    private String projectName;
    private String shortName;
    private String directCompany;
    private String manager;
    private String managerPhone;
    private String spaceCapacity;
    private String address;
    private String engineeringType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private String phase;
    private String description;
    private String ownerUnit;
    private String supervisionUnit;
    private String designUnit;
    private String contractor;
    private String contractorCreditCode;
    private String contractorLicenseNumber;
    private String generalContractNumber;
    private String projectClassification;
    private String investmentEntity;
    private String contractingMode;
    private BigDecimal contractAmount;
    private BigDecimal buildingArea;
    private String projectScale;
    private String projectTarget;
    private BigDecimal landArea;
    private BigDecimal buildingHeight;
    private String projectCategory;
    private BigDecimal excavationDepth;
    private Integer undergroundFloorCount;
    private Integer abovegroundFloorCount;
    private String qualityGoal;
    private String safetyGoal;
    private String greenConstructionGoal;
    private String projectLevel;
    private Integer managementStaffCount;
    private Integer attendanceCount;
    private Integer partyMemberCount;
    private String fixedIpAddress;
    private Integer profileVersion;
    private LocalDateTime updateTime;
    private boolean canEdit;
    private List<ProjectProfileImageVO> images = new ArrayList<>();
}
