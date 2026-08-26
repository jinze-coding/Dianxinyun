package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EdgeInspectionPointVO {
    private Long id;
    private Long projectId;
    private String pointCode;
    private String pointName;
    private String pointTypeCode;
    private String pointTypeName;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private String status;
    private Integer version;
    private Boolean hasGeneratedTasks;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
