package com.example.siteplatform.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProjectMapPointVO {
    private Long projectId;
    private Long id;
    private String projectName;
    private String shortName;
    private String projectStatus;
    private String currentStage;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateType;
    private String province;
    private String city;
    private String district;
    private String address;
    private Boolean hasLocation;
    private Long cameraTotal;
    private Long onlineCameraCount;
    private Long deviceTotal;
    private Long alarmDeviceCount;
    private Long fileTotal;
    private LocalDateTime lastUpdateTime;
}
