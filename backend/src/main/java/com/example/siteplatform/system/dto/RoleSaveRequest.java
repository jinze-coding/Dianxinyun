package com.example.siteplatform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RoleSaveRequest {
    @NotBlank
    @Size(max = 50)
    private String roleName;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,49}$", message = "角色编码格式不正确")
    private String roleCode;
    @Size(max = 200)
    private String description;
    @Pattern(regexp = "^(?i:PLATFORM|PROJECT)$", message = "角色范围只支持 PLATFORM 或 PROJECT")
    private String scopeType;
    @Min(0)
    @Max(1)
    private Integer enabled;
    private List<Long> menuIds;
    private List<Long> permissionIds;
    private List<String> businessModuleCodes;
}
