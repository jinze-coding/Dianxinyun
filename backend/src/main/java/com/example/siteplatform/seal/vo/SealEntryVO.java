package com.example.siteplatform.seal.vo;

import lombok.Data;

@Data
public class SealEntryVO {
    private String scene;
    private Long projectId;
    private String projectName;
    private String projectShortName;
    private String departmentName;
    private String companyName;
    private Long sealId;
    private String sealName;
    private Boolean active;
    private Boolean configured;
    private String qrStatus;
    private Integer qrVersion;
    private String message;
}
