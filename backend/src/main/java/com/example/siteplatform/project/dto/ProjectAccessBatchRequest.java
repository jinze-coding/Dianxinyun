package com.example.siteplatform.project.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class ProjectAccessBatchRequest {
    @NotNull @Size(min = 1, max = 200)
    private List<@NotNull @Positive Long> userIds;
    @NotNull
    private Operation operation;
    @Positive private Long projectId;
    @Positive private Long sourceProjectId;
    @Positive private Long targetProjectId;
    private RoleSource roleSource = RoleSource.SOURCE;
    @Size(max = 100)
    private List<@NotNull @Positive Long> roleIds = List.of();

    public enum Operation { ADD_ROLES, REMOVE_ROLES, REPLACE_ROLES, COPY_PROJECT, MOVE_PROJECT }
    public enum RoleSource { SOURCE, SELECTED }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isTransfer() { return operation == Operation.COPY_PROJECT || operation == Operation.MOVE_PROJECT; }
}
