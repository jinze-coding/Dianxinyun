package com.example.siteplatform.registration.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegistrationApplicationVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String applicationReason;
    private List<Long> desiredProjectIds;
    private List<RegistrationProjectOptionVO> desiredProjects;
    private String desiredProjectText;
    private String sourceType;
    private String registrationMode;
    private String phoneVerificationType;
    private String status;
    private Long createdUserId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;

    public String getSource() {
        return sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createTime;
    }

    public String getDesiredProjectName() {
        return desiredProjectText;
    }
}
