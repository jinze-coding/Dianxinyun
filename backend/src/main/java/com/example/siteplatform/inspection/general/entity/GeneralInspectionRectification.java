package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("general_inspection_rectification")
public class GeneralInspectionRectification {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long taskId;
    private Long taskItemId;
    private Long pointId;
    private String pointName;
    private String itemName;
    private String problemDesc;
    private String requirement;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private Long reviewerId;
    private String reviewerName;
    private String status;
    private String feedback;
    private String rectificationPhotoFileIds;
    private LocalDateTime completedTime;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Integer rejectCount;
    private LocalDateTime closeTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
