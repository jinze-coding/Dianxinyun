package com.example.siteplatform.project.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectAccessBatchPreview {
    private String confirmationToken;
    private String expiresAt;
    private int changedUserCount;
    private int unchangedUserCount;
    private int blockedUserCount;
    private int addedCount;
    private int updatedCount;
    private int removedCount;
    private long responsibilityCount;
    private List<UserChange> users = new ArrayList<>();

    @Data
    public static class UserChange {
        private Long userId;
        private String username;
        private String realName;
        private List<String> errors = new ArrayList<>();
        private List<ProjectChange> projects = new ArrayList<>();
    }

    @Data
    public static class ProjectChange {
        private Long projectId;
        private String projectName;
        private String action;
        private String beforeStatus;
        private String afterStatus;
        private List<Role> beforeRoles = List.of();
        private List<Role> afterRoles = List.of();
        private List<String> removedPermissions = List.of();
        private ResponsibilityImpactVO responsibilityImpact;
    }

    public record Role(Long id, String name) {}
}
