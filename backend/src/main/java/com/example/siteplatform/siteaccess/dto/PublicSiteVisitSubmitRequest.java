package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublicSiteVisitSubmitRequest {
    @NotBlank
    @Size(max = 64)
    private String inviteToken;
    @NotBlank
    @Size(max = 200)
    private String visitorCompany;
    @NotBlank
    @Size(max = 50)
    private String contactName;
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String contactPhone;
    @NotBlank
    @Size(max = 18)
    private String contactIdCard;
    @Valid
    @Size(max = 49)
    private List<SiteVisitPersonRequest> companions = new ArrayList<>();
    @NotBlank
    @Pattern(regexp = "^(DRIVING|OTHER)$", message = "出行方式不正确")
    private String travelMode;
    @Size(max = 20)
    private String vehiclePlate;
    @Size(max = 500)
    private String visitorRemark;
    @NotNull
    @AssertTrue(message = "请阅读并同意隐私告知")
    private Boolean privacyAgreed;
}
