package com.example.siteplatform.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WechatOperationLogVO {
    private Long id;
    private String operationType;
    private String operationDesc;
    private String operatorName;
    private LocalDateTime createTime;
}
