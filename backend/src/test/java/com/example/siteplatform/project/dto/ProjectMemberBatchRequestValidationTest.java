package com.example.siteplatform.project.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMemberBatchRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void batchRequiresExplicitNonEmptyChangesAndValidIdentifiers() {
        ProjectMemberBatchRequest request = new ProjectMemberBatchRequest();
        request.setChanges(List.of());
        assertFalse(validator.validate(request).isEmpty());

        ProjectMemberBatchRequest.Change change = new ProjectMemberBatchRequest.Change();
        change.setUserId(2L);
        change.setOperation("UPSERT");
        change.setRoleIds(List.of(30L));
        request.setChanges(List.of(change));
        assertTrue(validator.validate(request).isEmpty());

        change.setOperation("UNKNOWN");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void crossProjectBatchUsesProjectIdentifiers() {
        UserProjectRoleBatchRequest request = new UserProjectRoleBatchRequest();
        UserProjectRoleBatchRequest.Change change = new UserProjectRoleBatchRequest.Change();
        change.setProjectId(9L);
        change.setOperation("REMOVE");
        change.setRoleIds(List.of());
        request.setChanges(List.of(change));

        assertTrue(validator.validate(request).isEmpty());
    }
}
