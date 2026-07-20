package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_document_version")
public class ProjectDocumentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private Long fileResourceId;
    private String changeNote;
    private Long createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
