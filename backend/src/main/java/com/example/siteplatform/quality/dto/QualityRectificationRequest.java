package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QualityRectificationRequest {
    @NotBlank(message = "整改说明不能为空")
    @Size(max = 1000, message = "整改说明长度不能超过1000个字符")
    private String description;

    @NotEmpty(message = "请至少上传一张整改照片")
    @Size(max = 20, message = "整改照片不能超过20张")
    private List<@NotNull(message = "整改照片文件ID不能为空")
            @Positive(message = "整改照片文件ID必须为正数") Long> photoFileIds;
}
