package com.example.siteplatform.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device_info")
public class DeviceInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String deviceName;
    private String deviceCode;
    private String deviceType;
    private String status;
    private String height;
    private String maxLoad;
    private LocalDateTime lastReport;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
