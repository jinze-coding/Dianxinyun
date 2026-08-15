package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteGuardVisitQrVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String qrStatus;
    private Integer qrVersion;
    private Integer version;
    private String pagePath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
