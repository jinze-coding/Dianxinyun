package com.example.siteplatform.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatAccessApplicationVO {
    private Long id;
    private String phone;
    private String realName;
    private Long projectId;
    private String projectName;
    private Long sourceId;
    private String boxCode;
    private Long matchedUserId;
    private String matchedUsername;
    private String applicationType;
    private String status;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
