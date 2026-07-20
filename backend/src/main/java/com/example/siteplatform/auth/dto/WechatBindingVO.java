package com.example.siteplatform.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WechatBindingVO {
    private Long id;
    private String appId;
    private String phone;
    private String status;
    private LocalDateTime bindTime;
    private LocalDateTime lastLoginTime;
}
