package com.example.siteplatform.seal.vo;

import lombok.Data;

@Data
public class SealUserOptionVO {
    private Long userId;
    private String displayName;
    private String realName;
    private String username;
    private String phone;
    private Boolean defaultSelected;
    private Boolean selected;
    private Boolean activeProjectMember;
}
