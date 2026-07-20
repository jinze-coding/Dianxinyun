package com.example.siteplatform.notification.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WechatSubscriptionRequest {
    private Map<String, String> templateResults;
}
