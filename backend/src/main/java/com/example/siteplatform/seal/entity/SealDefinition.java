package com.example.siteplatform.seal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seal_definition")
public class SealDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String sealCode;
    private String sealName;
    private String sealType;
    private String companyName;
    private String status;
    private String sceneTokenHash;
    private String sceneTokenEncrypted;
    private String qrStatus;
    private Integer qrVersion;
    private Integer sortOrder;
    private Long createdBy;
    private Long updatedBy;
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
