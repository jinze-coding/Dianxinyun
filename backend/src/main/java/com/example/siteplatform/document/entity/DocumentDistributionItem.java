package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_distribution_item")
public class DocumentDistributionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Integer itemOrder;
    private Long documentId;
    private Long versionId;
    private String documentNoSnapshot;
    private String titleSnapshot;
    private String documentTypeSnapshot;
    private Integer systemVersionNo;
    private String externalRevisionSnapshot;
    private String fileNameSnapshot;
    private String sha256Snapshot;
    private LocalDateTime createTime;
}
