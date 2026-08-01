package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QualityReviewRequest {
    @NotNull(message = "复查结论不能为空")
    private Boolean passed;

    @Size(max = 1000, message = "复查意见长度不能超过1000个字符")
    private String comment;

    @Size(max = 20, message = "复查照片不能超过20张")
    private List<@NotNull(message = "复查照片文件ID不能为空")
            @Positive(message = "复查照片文件ID必须为正数") Long> photoFileIds;
}
