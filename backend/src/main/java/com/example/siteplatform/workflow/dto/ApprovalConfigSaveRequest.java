package com.example.siteplatform.workflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApprovalConfigSaveRequest {
    private String businessCode = "SEAL_APPLICATION";
    @NotNull(message = "项目不能为空")
    private Long projectId;
    @NotNull(message = "印章不能为空")
    private Long sealId;
    private Boolean enabled = true;
    @Size(max = 20, message = "审批人不能超过20人")
    private List<Long> approverUserIds = new ArrayList<>();
    @Size(max = 100, message = "默认抄送人不能超过100人")
    private List<Long> defaultCcUserIds = new ArrayList<>();
}
