package com.example.siteplatform.electricbox.dto;

import lombok.Data;

@Data
public class ElectricBoxRequest {
    private Long projectId;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private Long responsibleElectricianId;
    private String responsibleElectricianName;
    private Long safetyManagerId;
    private String safetyManagerName;
    private String qrCode;
    private String qrStatus;
    private String status;
    private Integer publicAccessEnabled;
    private String remark;
}
