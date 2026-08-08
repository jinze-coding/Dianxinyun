package com.example.siteplatform.seal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SealApplicationSaveRequest {
    @Size(max = 64, message = "requestKey不能超过64个字符")
    private String requestKey;
    @Size(max = 64, message = "scene格式不正确")
    private String scene;
    private Long projectId;
    private Long sealId;
    @Size(max = 100, message = "申请部门/项目部不能超过100个字符")
    private String departmentName;
    @NotBlank(message = "用印事由不能为空")
    @Size(max = 1000, message = "用印事由不能超过1000个字符")
    private String purpose;
    @Valid
    @NotEmpty(message = "至少填写一项用印文件")
    @Size(max = 20, message = "用印文件不能超过20项")
    private List<SealApplicationItemRequest> items = new ArrayList<>();
    @Size(max = 100, message = "抄送人不能超过100人")
    private List<Long> ccUserIds;
}
