package com.example.siteplatform.seal.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SealCcRecipientVO {
    private Long userId;
    private String displayName;
    private LocalDateTime readTime;
}
