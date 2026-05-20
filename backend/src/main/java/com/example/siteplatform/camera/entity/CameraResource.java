package com.example.siteplatform.camera.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("camera_resource")
public class CameraResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String cameraName;
    private String cameraCode;
    private String area;
    private String cameraType;
    private String rtspUrl;
    private Integer onlineStatus;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
