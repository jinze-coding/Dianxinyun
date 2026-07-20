package com.example.siteplatform.inspection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_template_item")
public class InspectionTemplateItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;
    private String templateCode;
    private String itemCode;
    private String itemName;
    private String inputType;
    private Integer required;
    private Integer sortOrder;
    private String abnormalRequirement;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
