package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WechatProjectAccessRequest {
    @NotBlank(message = "巡检场景码不能为空")
    @Size(max = 256)
    private String scene;
}
