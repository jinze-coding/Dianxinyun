package com.example.siteplatform.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file_resource")
public class FileResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String fileName;
    private String fileType;
    @JsonIgnore
    private String filePath;
    private Long fileSize;
    private String businessType;
    private Long businessId;
    private Long uploaderId;

    private String storageProvider;
    @JsonIgnore
    private String storageKey;
    private String originalFileName;
    private String mimeType;
    private String fileExtension;
    private String sha256;

    @TableField(exist = false)
    private String uploaderName;

    private String status;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
