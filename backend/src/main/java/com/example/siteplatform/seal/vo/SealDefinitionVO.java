package com.example.siteplatform.seal.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SealDefinitionVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String sealCode;
    private String sealName;
    private String sealType;
    private String companyName;
    private String status;
    private Boolean enabled;
    private String qrStatus;
    private Boolean qrEnabled;
    private Integer qrVersion;
    private Integer sortOrder;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
