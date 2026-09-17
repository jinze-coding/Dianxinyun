package com.example.siteplatform.system.userimport;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("system_user_import_batch")
public class UserImportBatch {
    @TableId(type = IdType.AUTO) private Long id;
    private Long createdBy;
    private String createdByName;
    private String status;
    private Integer personCount;
    private Integer newCount;
    private Integer skippedCount;
    private Integer errorCount;
    private Integer preparedCount;
    private String message;
    private String requestKey;
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String temporaryPasswordCipher;
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String confirmationPasswordHash;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
