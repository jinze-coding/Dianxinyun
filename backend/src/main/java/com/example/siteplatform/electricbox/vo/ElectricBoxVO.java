package com.example.siteplatform.electricbox.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ElectricBoxVO {
    private Long id;
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
    private String publicCode;
    private Integer publicAccessEnabled;
    private String remark;
    private LocalDate lastCheckDate;
    private String todayStatus;
    private Integer pendingRectificationCount;
    private Boolean inspectionRequired;
    private LocalDate scopeEffectiveDate;
    private LocalDate scopeEndDate;
}
