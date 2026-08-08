package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteVisitInvitationCreateRequest {
    @NotNull
    @Positive
    private Long projectId;
    @NotNull
    private LocalDateTime visitStartTime;
    @NotNull
    private LocalDateTime visitEndTime;
    @NotBlank
    @Size(max = 300)
    private String purpose;
    @NotBlank
    @Size(max = 200)
    private String visitLocation;
    @NotNull
    @Positive
    private Long hostUserId;
    @Size(max = 500)
    private String internalRemark;
}
