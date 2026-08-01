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
    /** 微信快捷注册审批通过后，首次业务访问前必须完成初始密码设置。 */
    private Boolean initialPasswordSetupRequired;
    private Boolean wechatBound;
    private String wechatBindingStatus;
    private List<String> roles;
    private List<Long> accessibleProjectIds;
    private List<UserProjectRoleVO> projectRoles;
    private List<UserProjectRoleVO> projectContexts;
    private List<String> permissionCodes;
    private List<MenuVO> menus;
}
