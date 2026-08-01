package com.example.siteplatform.system.dto;

import lombok.Data;

import java.util.List;

/** 角色业务模块、菜单和操作权限的一次性保存请求。 */
@Data
public class RolePermissionUpdateRequest {
    private List<Long> menuIds;
    private List<Long> permissionIds;
    private List<String> businessModuleCodes;
}
