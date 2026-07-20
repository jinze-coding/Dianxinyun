package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WechatBindingStatusRequest {
    private String status;
    private String reason;
}
