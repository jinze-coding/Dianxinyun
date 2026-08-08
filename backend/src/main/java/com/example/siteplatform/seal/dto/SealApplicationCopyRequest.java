package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SealApplicationCopyRequest {
    @NotBlank(message = "requestKey不能为空")
    @Size(max = 64, message = "requestKey不能超过64个字符")
    private String requestKey;
    @Size(max = 100, message = "抄送人不能超过100人")
    private List<Long> ccUserIds;
}
