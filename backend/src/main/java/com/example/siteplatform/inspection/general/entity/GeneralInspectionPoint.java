package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_point")
public class GeneralInspectionPoint {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private String pointCode;
    private String pointName;
    private Long categoryId;
    private String categoryName;
    private String areaName;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private String riskNote;
    private String referencePhotoFileIds;
    private Integer qrEnabled;
    private String publicCode;
    private Integer qrVersion;
    private Integer publicAccessEnabled;
    private String status;
    private Integer version;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
