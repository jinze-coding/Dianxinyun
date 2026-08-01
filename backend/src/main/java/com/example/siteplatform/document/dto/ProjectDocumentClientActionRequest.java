package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProjectDocumentClientActionRequest {
    @NotBlank(message = "客户端操作不能为空")
    @Pattern(
            regexp = "^(OPEN_SAVE_MENU|SHARE_WECHAT_FILE)$",
            message = "客户端操作只支持 OPEN_SAVE_MENU 或 SHARE_WECHAT_FILE"
    )
    private String action;
    private Long versionId;
}
