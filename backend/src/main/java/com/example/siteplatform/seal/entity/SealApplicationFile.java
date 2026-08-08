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
@TableName("seal_application_file")
public class SealApplicationFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long itemId;
    private Long projectId;
    private Long fileResourceId;
    private String fileRole;
    private Long uploaderId;
    private String uploaderName;
    private Long archivedDocumentId;
    private Long archivedVersionId;
    private LocalDateTime archivedTime;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
