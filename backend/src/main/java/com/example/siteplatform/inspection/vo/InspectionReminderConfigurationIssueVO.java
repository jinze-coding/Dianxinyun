package com.example.siteplatform.inspection.vo;

import lombok.Data;

@Data
public class InspectionReminderConfigurationIssueVO {
    private Long boxId;
    private String boxCode;
    private String boxName;
    private Long responsibleUserId;
    private String responsibleUserName;
    private String reason;
}
