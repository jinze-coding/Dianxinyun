package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealEntryResolveRequest {
    @NotBlank(message = "scene不能为空")
    @Size(max = 64, message = "scene格式不正确")
    private String scene;
}
