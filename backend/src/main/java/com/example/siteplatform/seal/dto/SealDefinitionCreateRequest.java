package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SealDefinitionCreateRequest extends SealDefinitionRequest {
    @Override
    @NotNull(message = "项目不能为空")
    public Long getProjectId() {
        return super.getProjectId();
    }

    @Override
    @NotBlank(message = "印章编码不能为空")
    public String getSealCode() {
        return super.getSealCode();
    }
}
