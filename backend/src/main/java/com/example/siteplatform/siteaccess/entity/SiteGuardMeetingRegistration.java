package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_guard_meeting_registration")
public class SiteGuardMeetingRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long guardRegistrationId;
    private Long invitationId;
    private Long meetingRegistrationId;
    private LocalDateTime createTime;
}
