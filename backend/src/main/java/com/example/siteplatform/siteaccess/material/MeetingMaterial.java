package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_meeting_material")
public class MeetingMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invitationId;
    private Long projectId;
    private String title;
    private String category;
    private String description;
    private Long currentVersionId;
    private Long publishedVersionId;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
