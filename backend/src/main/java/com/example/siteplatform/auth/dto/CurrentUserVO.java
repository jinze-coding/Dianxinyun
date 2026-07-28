package com.example.siteplatform.auth.dto;

import lombok.Data;
import com.example.siteplatform.system.dto.MenuVO;

import java.util.List;

@Data
public class CurrentUserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private Integer passwordLoginEnabled;
    private Boolean wechatBound;
    private String wechatBindingStatus;
    private List<String> roles;
    private List<Long> accessibleProjectIds;
    private List<UserProjectRoleVO> projectRoles;
    private List<UserProjectRoleVO> projectContexts;
    private List<String> permissionCodes;
    private List<MenuVO> menus;
}
