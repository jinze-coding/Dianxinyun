package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteGuardVisitMiniCodeVO {
    private Long guardQrId;
    private Long projectId;
    private String projectName;
    private String qrStatus;
    private Integer qrVersion;
    private String pagePath;
    private String codeType;
    private String imageMimeType;
    private String imageContent;
    private String hint;
}
