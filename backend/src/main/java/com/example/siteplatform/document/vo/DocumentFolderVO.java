package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentFolderVO {
    private Long id;
    private Long projectId;
    private Long parentId;
    private String folderName;
    private Integer sortNo;
    private Long documentCount;
    private LocalDateTime updateTime;
}
