package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealEntryStatusRequest {
    @NotNull(message = "二维码状态不能为空")
    private Boolean enabled;
    @Size(max = 300, message = "原因不能超过300个字符")
    private String reason;
}
