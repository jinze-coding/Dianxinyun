package com.example.siteplatform.seal.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SealApplicationLogVO {
    private Long id;
    private String action;
    private String actionLabel;
    private Long operatorId;
    private String operatorName;
    private String opinion;
    private String description;
    private LocalDateTime createTime;
}
