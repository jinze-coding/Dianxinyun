package com.example.siteplatform.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class CurrentUserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private List<String> roles;
    private List<Long> accessibleProjectIds;
    private List<UserProjectRoleVO> projectRoles;
}
