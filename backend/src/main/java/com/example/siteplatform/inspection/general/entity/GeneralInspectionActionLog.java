package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_action_log")
public class GeneralInspectionActionLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private String businessType;
    private Long businessId;
    private String actionType;
    private Long operatorId;
    private String operatorName;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createTime;
}
