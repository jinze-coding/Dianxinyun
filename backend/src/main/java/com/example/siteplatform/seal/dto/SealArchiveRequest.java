package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SealArchiveRequest {
    @NotBlank(message = "归档方式不能为空")
    @Pattern(regexp = "NEW_DOCUMENT|NEW_VERSION", message = "归档方式不正确")
    private String archiveMode;
    @NotNull(message = "归档文件不能为空")
    private Long fileId;
    private Long folderId;
    private Long documentId;
    @Size(max = 100, message = "资料编号不能超过100个字符")
    private String documentNo;
    @Size(max = 200, message = "资料名称不能超过200个字符")
    private String title;
    @Size(max = 500, message = "版本说明不能超过500个字符")
    private String changeNote;
}
