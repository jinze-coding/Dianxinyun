package com.example.siteplatform.registration.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegistrationReviewRequest {
    private List<Long> roleIds;
    private List<ProjectAssignment> projectAssignments;
    private String reviewComment;

    @Data
    public static class ProjectAssignment {
        private Long projectId;
        private List<Long> roleIds;
    }
}
