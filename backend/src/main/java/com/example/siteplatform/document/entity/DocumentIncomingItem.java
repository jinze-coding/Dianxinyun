package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_incoming_item")
public class DocumentIncomingItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long fileResourceId;
    private Long folderId;
    private Integer itemOrder;
    private String title;
    private String documentNo;
    private String documentType;
    private String externalRevision;
    private String matchMode;
    private Long targetDocumentId;
    private String duplicateRevisionReason;
    private String changeNote;
    private Long publishedDocumentId;
    private Long publishedVersionId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
