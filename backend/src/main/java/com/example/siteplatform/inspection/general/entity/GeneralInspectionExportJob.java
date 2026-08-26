package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("general_inspection_export_job")
public class GeneralInspectionExportJob {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long requestedById;
    private String requestedByName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer progress;
    private Long fileResourceId;
    private String errorMessage;
    private LocalDateTime expiresTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
