package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("general_inspection_export_job_task")
public class GeneralInspectionExportJobTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long jobId;
    private Long taskId;
    private Integer itemOrder;
    private LocalDate occurrenceDate;
    private LocalDateTime createTime;
}
