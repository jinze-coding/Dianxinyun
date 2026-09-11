package com.example.siteplatform.seal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("seal_form_export_item")
public class SealFormExportItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long jobId;
    private Long projectId;
    private Long applicationId;
    private Integer itemOrder;
}
