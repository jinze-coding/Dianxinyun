package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteVisitAuditVO {
    private Long id;
    private String actionType;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private LocalDateTime createTime;
}
