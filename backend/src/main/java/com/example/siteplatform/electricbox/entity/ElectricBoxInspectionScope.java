package com.example.siteplatform.electricbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("electric_box_inspection_scope")
public class ElectricBoxInspectionScope {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long electricBoxId;
    private Integer included;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
