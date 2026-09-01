package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_distribution_recipient_item")
public class DocumentDistributionRecipientItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long recipientId;
    private Long distributionItemId;
    private Integer paperCopyCount;
    private LocalDateTime createTime;
}
