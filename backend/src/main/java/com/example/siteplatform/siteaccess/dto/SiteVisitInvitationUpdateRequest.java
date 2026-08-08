package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SiteVisitInvitationUpdateRequest {
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

    @Size(max = 200)
    private String visitorCompany;
    @Size(max = 50)
    private String contactName;
    @Pattern(regexp = "^(?:|1[3-9]\\d{9})$", message = "手机号格式不正确")
    private String contactPhone;
    @Size(max = 18)
    private String contactIdCard;
    @Valid
    @Size(max = 49)
    private List<SiteVisitPersonRequest> companions = new ArrayList<>();
    @Pattern(regexp = "^(?:|DRIVING|OTHER)$", message = "出行方式不正确")
    private String travelMode;
    @Size(max = 20)
    private String vehiclePlate;
    @Size(max = 500)
    private String visitorRemark;
}
