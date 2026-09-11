package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_meeting_material_version")
public class MeetingMaterialVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private Long invitationId;
    private Long projectId;
    private Integer versionNo;
    private Long fileId;
    private String publicCode;
    private String uploadKey;
    private Long uploaderId;
    private String uploaderName;
    private String changeNote;
    private LocalDateTime createTime;
}
