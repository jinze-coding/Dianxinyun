package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealTransferRequest {
    @NotNull(message = "新审批人不能为空")
    private Long assigneeUserId;
    @NotBlank(message = "转办原因不能为空")
    @Size(max = 500, message = "转办原因不能超过500个字符")
    private String reason;
}
