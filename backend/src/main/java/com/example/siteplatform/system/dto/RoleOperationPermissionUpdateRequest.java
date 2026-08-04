package com.example.siteplatform.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 仅更新角色操作权限，不改变菜单可见性。 */
@Data
public class RoleOperationPermissionUpdateRequest {
    @NotNull
    @Size(max = 500)
    private List<@Positive Long> permissionIds;
}
