package com.example.siteplatform.quality.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QualityWeeklyDraftItemVO {
    private Long id;
    private String itemKey;
    private Integer itemOrder;
    private String title;
    private String location;
    private String description;
    private String severity;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private List<Long> beforePhotoFileIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
