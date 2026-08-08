package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealApplicationItemRequest {
    private Long id;
    @NotBlank(message = "用印文件名称不能为空")
    @Size(max = 200, message = "用印文件名称不能超过200个字符")
    private String documentName;
    @Min(value = 1, message = "文件份数至少为1")
    @Max(value = 999, message = "文件份数不能超过999")
    private Integer copies;
}
