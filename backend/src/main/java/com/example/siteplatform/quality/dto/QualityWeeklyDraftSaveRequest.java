package com.example.siteplatform.quality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QualityWeeklyDraftSaveRequest {
    @NotNull(message = "expectedVersion不能为空")
    @PositiveOrZero(message = "expectedVersion不能为负数")
    private Integer expectedVersion;

    private LocalDate inspectionDate;

    @Size(max = 1000, message = "检查结论长度不能超过1000个字符")
    private String conclusion;

    @Size(max = 20, message = "周检现场照片不能超过20张")
    private List<@Positive(message = "周检现场照片文件ID必须为正数") Long> overviewPhotoFileIds;

    @Valid
    @Size(max = 50, message = "每份周检最多录入50个问题")
    private List<QualityWeeklyDraftItemRequest> items;
}
