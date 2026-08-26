package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QualityWeeklyDraftItemRequest {
    @Size(max = 100, message = "问题草稿键长度不能超过100个字符")
    private String itemKey;

    @Min(value = 1, message = "问题顺序必须从1开始")
    @Max(value = 50, message = "问题顺序不能超过50")
    private Integer itemOrder;

    @Size(max = 200, message = "质量问题标题长度不能超过200个字符")
    private String title;

    @Size(max = 200, message = "问题位置长度不能超过200个字符")
    private String location;

    @Size(max = 1000, message = "问题描述长度不能超过1000个字符")
    private String description;

    @Size(max = 20, message = "严重程度长度不能超过20个字符")
    private String severity;

    @Positive(message = "整改负责人ID必须为正数")
    private Long assigneeId;

    private LocalDate deadline;

    @Size(max = 20, message = "整改前照片不能超过20张")
    private List<@Positive(message = "整改前照片文件ID必须为正数") Long> beforePhotoFileIds;
}
