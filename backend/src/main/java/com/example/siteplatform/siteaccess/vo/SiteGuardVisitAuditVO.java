package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteGuardVisitAuditVO {
    private String actionType;
    private String operatorName;
    private String comment;
    private LocalDateTime createTime;
}
