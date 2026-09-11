package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_meeting_material_preview")
public class MeetingMaterialPreview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Long invitationId;
    private String kind;
    private String status;
    private Long fileId;
    private String failureMessage;
    private String workerId;
    private LocalDateTime leaseUntil;
    private Integer attempts;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
