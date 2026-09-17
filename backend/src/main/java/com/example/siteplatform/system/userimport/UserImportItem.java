package com.example.siteplatform.system.userimport;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@TableName("system_user_import_item")
public class UserImportItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long batchId;
    @TableField("excel_row") private Integer rowNumber;
    private String realName;
    private String phone;
    private Long projectId;
    private Long roleId;
    private String projectLabel;
    private String roleLabel;
    private String status;
    private String message;
    private Long userId;
    private Integer credentialVersion;
    private Long credentialOwnerId;
    @JsonIgnore @ToString.Exclude private String credentialCipher;
    private LocalDateTime downloadUntil;
    private LocalDateTime passwordExpiresAt;
}
