package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealDefinitionRequest {
    private Long projectId;
    @Size(max = 40, message = "印章编码不能超过40个字符")
    private String sealCode;
    @NotBlank(message = "印章名称不能为空")
    @Size(max = 100, message = "印章名称不能超过100个字符")
    private String sealName;
    @Size(max = 30, message = "印章类型不能超过30个字符")
    private String sealType;
    @Size(max = 200, message = "公司名称不能超过200个字符")
    private String companyName;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer version;
}
