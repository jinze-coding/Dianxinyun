package com.example.siteplatform.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_info")
public class ProjectInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectName;
    private String shortName;
    private String area;
    private String period;
    private String phase;
    private String projectStatus;
    private String safetyGoal;
    private String qualityGoal;
    private String manager;
    private String contractor;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private String province;
    private String city;
    private String district;
    private String address;
    private String coordinateType;

    /* 项目档案扩展字段只通过专用 profile 接口返回，避免项目列表泄露联系方式、合同和固定 IP。 */
    @JsonIgnore private String directCompany;
    @JsonIgnore private String managerPhone;
    @JsonIgnore private String spaceCapacity;
    @JsonIgnore private String engineeringType;
    @JsonIgnore private LocalDate actualStartDate;
    @JsonIgnore private LocalDate actualEndDate;
    @JsonIgnore private String ownerUnit;
    @JsonIgnore private String supervisionUnit;
    @JsonIgnore private String designUnit;
    @JsonIgnore private String contractorCreditCode;
    @JsonIgnore private String contractorLicenseNumber;
    @JsonIgnore private String generalContractNumber;
    @JsonIgnore private String projectClassification;
    @JsonIgnore private String investmentEntity;
    @JsonIgnore private String contractingMode;
    @JsonIgnore private BigDecimal contractAmount;
    @JsonIgnore private BigDecimal buildingArea;
    @JsonIgnore private String projectScale;
    @JsonIgnore private String projectTarget;
    @JsonIgnore private BigDecimal landArea;
    @JsonIgnore private BigDecimal buildingHeight;
    @JsonIgnore private String projectCategory;
    @JsonIgnore private BigDecimal excavationDepth;
    @JsonIgnore private Integer undergroundFloorCount;
    @JsonIgnore private Integer abovegroundFloorCount;
    @JsonIgnore private String greenConstructionGoal;
    @JsonIgnore private String projectLevel;
    @JsonIgnore private Integer managementStaffCount;
    @JsonIgnore private Integer attendanceCount;
    @JsonIgnore private Integer partyMemberCount;
    @JsonIgnore private String fixedIpAddress;
    @JsonIgnore private Integer profileVersion;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
