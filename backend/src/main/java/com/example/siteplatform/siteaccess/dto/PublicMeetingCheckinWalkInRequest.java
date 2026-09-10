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
public class PublicMeetingCheckinWalkInRequest {
    @NotBlank @Size(max = 200)
    private String visitorCompany;
    @NotBlank @Size(max = 50)
    private String contactName;
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String contactPhone;
    @Valid @Size(max = 49)
    private List<WalkInCompanion> companions = new ArrayList<>();
    @NotBlank @Pattern(regexp = "^(DRIVING|OTHER)$", message = "出行方式不正确")
    private String travelMode;
    @Size(max = 20)
    private String vehiclePlate;
    @Size(max = 500)
    private String visitorRemark;
    @NotNull @AssertTrue(message = "请阅读并同意隐私告知")
    private Boolean privacyAgreed;
    @Valid @NotNull
    private MeetingCheckinLocationRequest location;

    @Data
    public static class WalkInCompanion {
        @Size(max = 200)
        private String personCompany;
        @NotBlank @Size(max = 50)
        private String personName;
        @Pattern(regexp = "^(?:|1[3-9]\\d{9})$", message = "同行人员手机号码格式不正确")
        private String personPhone;
    }
}
