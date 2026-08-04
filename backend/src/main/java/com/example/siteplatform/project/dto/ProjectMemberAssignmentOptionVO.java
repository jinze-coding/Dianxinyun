package com.example.siteplatform.project.dto;

import com.example.siteplatform.system.entity.SystemRole;
import lombok.Data;

import java.util.List;

@Data
public class ProjectMemberAssignmentOptionVO {
    private Long userId;
    private String username;
    private String realName;
    private Integer accountStatus;
    private boolean assigned;
    private String accessStatus;
    private String statusReason;
    private List<SystemRole> projectRoles;
    private boolean protectedManager;
    private boolean roleEditable;
    private boolean removable;
}
