package com.example.siteplatform.document.vo;

import lombok.Data;

@Data
public class DocumentRecipientOptionVO {
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String roleNames;
    private Boolean mandatory;
}
