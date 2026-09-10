package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicMeetingCheckinSessionRequest {
    @NotBlank @Size(max = 64)
    private String sceneToken;
    @NotBlank @Size(max = 256)
    private String wechatCode;
}
