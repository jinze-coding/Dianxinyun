package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicGuardVisitorSessionRequest {
    @NotBlank
    @Size(max = 64)
    private String sceneToken;
    @NotBlank
    @Size(max = 128)
    private String wechatCode;
}
