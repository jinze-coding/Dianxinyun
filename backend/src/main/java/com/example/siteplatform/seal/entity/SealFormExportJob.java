package com.example.siteplatform.seal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("seal_form_export_job")
public class SealFormExportJob {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long requestedById;
    private String requestedByName;
    private String requestKey;
    private String requestHash;
    private String selectionMode;
    private String status;
    private Integer applicationCount;
    private Integer processedCount;
    private Integer pageCount;
    private Integer attempts;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String leaseOwner;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime leaseUntil;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long fileResourceId;
    private String fileName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String errorMessage;
    private LocalDateTime expiresTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
