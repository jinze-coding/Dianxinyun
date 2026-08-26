package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_event_outbox")
public class GeneralInspectionEventOutbox {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventKey;
    private String eventType;
    private Long projectId;
    private String businessType;
    private Long businessId;
    private String payloadJson;
    private String status;
    private LocalDateTime occurredTime;
    private LocalDateTime publishedTime;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
