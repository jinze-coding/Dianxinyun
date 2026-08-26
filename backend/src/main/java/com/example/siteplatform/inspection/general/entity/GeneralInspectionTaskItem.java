package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_task_item")
public class GeneralInspectionTaskItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long templateItemId;
    private String itemKey;
    private String itemName;
    private String guidance;
    private String standardReference;
    private Integer allowNa;
    private Integer normalPhotoMin;
    private Integer abnormalPhotoMin;
    private Integer photoMax;
    private Integer normalDescriptionRequired;
    private Integer abnormalDescriptionRequired;
    private Integer sortOrder;
    private String result;
    private String description;
    private String photoFileIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
