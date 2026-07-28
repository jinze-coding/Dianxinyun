package com.example.siteplatform.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RegistrationSubmitRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{3,31}$", message = "账号需以字母开头，长度4-32位")
    private String username;
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
    @NotBlank
    @Size(max = 50)
    private String realName;
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
    @Email
    @Size(max = 100)
    private String email;
    @Size(max = 500)
    private String applicationReason;
    @Size(max = 500)
    private String reason;
    @Size(max = 50, message = "意向项目最多选择50个")
    private List<@Positive(message = "项目ID必须为正数") Long> desiredProjectIds;
    @Size(max = 200)
    private String desiredProjectText;
    @Size(max = 200)
    private String desiredProjectName;
    @Positive(message = "项目ID必须为正数")
    private Long requestedProjectId;
    @Size(max = 20)
    private String sourceType;
    @Size(max = 20)
    private String source;
    @Size(max = 128)
    private String captchaId;
    @Size(max = 20)
    private String captchaCode;
    @Size(max = 128)
    private String wechatSessionToken;
    @Size(max = 512)
    private String wechatCode;
    @Size(max = 512)
    private String phoneCode;
    @Size(max = 30)
    private String phoneVerificationType;
}
