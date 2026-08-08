package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealDecisionRequest {
    @NotBlank(message = "项目经理审批意见不能为空")
    @Size(max = 1000, message = "审批意见不能超过1000个字符")
    private String opinion;
}
