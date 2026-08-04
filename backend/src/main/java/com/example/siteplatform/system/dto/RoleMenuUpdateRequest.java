package com.example.siteplatform.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 仅更新角色菜单与跨端业务模块，不直接修改操作权限。 */
@Data
public class RoleMenuUpdateRequest {
    @NotNull
    @Size(max = 500)
    private List<@Positive Long> menuIds;
    @NotNull
    @Size(max = 3)
    private List<@Pattern(regexp = "^(DOCUMENT|INSPECTION|QUALITY)$") String> businessModuleCodes;
}
