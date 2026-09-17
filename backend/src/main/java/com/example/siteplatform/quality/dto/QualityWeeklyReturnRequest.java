package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QualityWeeklyReturnRequest extends QualityWeeklyActionRequest {
    @NotBlank(message = "请填写退回原因")
    @Size(max = 200, message = "退回原因不能超过200个字符")
    private String reason;
}
