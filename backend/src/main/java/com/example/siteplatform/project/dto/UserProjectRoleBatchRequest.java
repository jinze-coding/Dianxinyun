package com.example.siteplatform.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserProjectRoleBatchRequest {
    @NotNull
    @Size(min = 1, max = 200)
    private List<@Valid Change> changes;

    /** 存在未完成职责时，由管理员在影响预览后明确确认解除责任绑定。 */
    private boolean confirmResponsibilityRelease;

    @Data
    public static class Change {
        @NotNull
        @Positive
        private Long projectId;

        @NotBlank
        @Pattern(regexp = "^(?i:UPSERT|REMOVE)$", message = "操作只支持 UPSERT 或 REMOVE")
        private String operation;

        @Size(max = 100)
        private List<@NotNull @Positive Long> roleIds;
    }
}
