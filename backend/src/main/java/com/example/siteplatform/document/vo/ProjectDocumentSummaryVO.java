package com.example.siteplatform.document.vo;

import lombok.Data;

@Data
public class ProjectDocumentSummaryVO {
    private Long total;
    private Long active;
    /** 历史兼容字段，正式界面不再使用资料类型统计。 */
    @Deprecated
    private Long drawings;
    /** 历史兼容字段，正式界面不再使用资料类型统计。 */
    @Deprecated
    private Long forms;
    private Long archived;
    private Long recentUpdates;
    private Boolean canManage;
}
