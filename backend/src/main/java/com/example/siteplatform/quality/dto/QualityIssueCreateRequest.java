package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QualityIssueCreateRequest {
    @NotNull(message = "项目ID不能为空")
    @Positive(message = "项目ID必须为正数")
    private Long projectId;

    @Size(max = 100, message = "requestKey长度不能超过100个字符")
    private String requestKey;

    @NotBlank(message = "质量问题标题不能为空")
    @Size(max = 200, message = "质量问题标题长度不能超过200个字符")
    private String title;

    @Size(max = 200, message = "问题位置长度不能超过200个字符")
    private String location;

    @Size(max = 1000, message = "问题描述长度不能超过1000个字符")
    private String description;

    @Pattern(
            regexp = "^(?i:NORMAL|WARNING|DANGER)$",
            message = "严重程度只支持 NORMAL、WARNING 或 DANGER")
    private String severity;

    @Positive(message = "整改负责人ID必须为正数")
    private Long assigneeId;

    @FutureOrPresent(message = "闭环期限不能早于今天")
    private LocalDate deadline;

    @NotEmpty(message = "请至少上传一张问题照片")
    @Size(max = 20, message = "问题照片不能超过20张")
    private List<@NotNull(message = "问题照片文件ID不能为空")
            @Positive(message = "问题照片文件ID必须为正数") Long> photoFileIds;
}
