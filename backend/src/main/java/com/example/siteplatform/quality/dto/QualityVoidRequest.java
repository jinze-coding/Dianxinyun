package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QualityVoidRequest {
    @NotBlank(message = "作废原因不能为空")
    @Size(max = 1000, message = "作废原因长度不能超过1000个字符")
    private String comment;
}
