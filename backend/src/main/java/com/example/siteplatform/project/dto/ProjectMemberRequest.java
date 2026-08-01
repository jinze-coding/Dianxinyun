package com.example.siteplatform.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectMemberRequest {
    private Long projectId;
    private Long userId;
    /** 当前项目的全部角色；多个角色的权限按并集生效。 */
    private List<Long> roleIds;
    /**
     * 仅用于旧客户端过渡读取，新的写入请求不得再使用。
     */
    @Deprecated
    private String projectRoleCode;
    @Deprecated
    private Long permissionTemplateId;
}
