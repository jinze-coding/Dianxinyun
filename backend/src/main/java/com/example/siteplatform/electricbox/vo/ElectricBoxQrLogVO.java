package com.example.siteplatform.electricbox.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ElectricBoxQrLogVO {
    private Long id;
    private Long projectId;
    private Long electricBoxId;
    private String boxCode;
    private String actionType;
    private String qrType;
    private String oldQrCode;
    private String newQrCode;
    private Long operatorUserId;
    private String operatorUsername;
    private String reason;
    private LocalDateTime createTime;
}
