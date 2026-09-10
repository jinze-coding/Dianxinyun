package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteMeetingAttendeeUpdateRequest {
    @NotBlank @Size(max = 50)
    private String personName;
    @Size(max = 200)
    private String personCompany;
    @Pattern(regexp = "^(?:|1[3-9]\\d{9})$", message = "手机号码格式不正确")
    private String personPhone;
    @NotBlank @Size(max = 300)
    private String reason;
    @NotNull @Min(0)
    private Integer version;
}
