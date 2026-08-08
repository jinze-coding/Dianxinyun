package com.example.siteplatform.inspection.vo;

import lombok.Data;

@Data
public class InspectionAssigneeVO {
    private Long userId;
    private String username;
    private String realName;
    private String displayName;
}
