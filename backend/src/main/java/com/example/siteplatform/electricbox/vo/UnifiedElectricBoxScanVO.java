package com.example.siteplatform.electricbox.vo;

import lombok.Data;

import java.util.List;

@Data
public class UnifiedElectricBoxScanVO {
    private String sceneCode;
    private String mode;
    private String reason;
    private Long electricBoxId;
    private Long projectId;
    private String publicCode;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String status;
    private Boolean publicAccessEnabled;
    private Boolean inspectionRequired;
    private Boolean authenticated;
    private Boolean projectAuthorized;
    private String directAction;
    private Long todayRecordId;
    private List<String> allowedActions;
}
