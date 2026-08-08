package com.example.siteplatform.workcenter.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class InboxNotificationVO {
    private Long id;
    private Long notificationId;
    private Long projectId;
    private String projectName;
    private String businessType;
    private Long businessId;
    private Long targetId;
    private String eventCode;
    private String title;
    private String summary;
    private Boolean isRead;
    private Boolean read;
    private String readStatus;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
    private LocalDateTime createdAt;
    private String routeCode;
    private Map<String, Object> routeParams;
}
