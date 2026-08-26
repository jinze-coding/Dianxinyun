package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralInspectionTaskServiceStatusTest {

    @Test
    void lateMarkerMustNotHideRectificationWorkflowState() {
        GeneralInspectionTask task = lateTask("RECTIFICATION_PENDING");
        assertThat(GeneralInspectionTaskService.displayStatus(task, false))
                .isEqualTo("RECTIFICATION_PENDING");

        task.setStatus("CLOSED");
        assertThat(GeneralInspectionTaskService.displayStatus(task, false)).isEqualTo("CLOSED");
    }

    @Test
    void lateCompletedTaskUsesLateCompletedDisplayState() {
        assertThat(GeneralInspectionTaskService.displayStatus(lateTask("COMPLETED"), false))
                .isEqualTo("LATE_COMPLETED");
    }

    @Test
    void unsubmittedOverdueTaskUsesMissedDisplayState() {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setStatus("PENDING");
        assertThat(GeneralInspectionTaskService.displayStatus(task, true)).isEqualTo("OVERDUE_MISSED");
    }

    @Test
    void taskReassignmentCannotRewriteSubmittedInspectionAssignment() {
        assertThatCode(() -> GeneralInspectionTaskService.requireTaskReassignable("PENDING"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GeneralInspectionTaskService.requireTaskReassignable("RECTIFICATION_PENDING"))
                .isInstanceOfSatisfying(com.example.siteplatform.common.BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
    }

    @Test
    void taskReassignmentCapabilityRequiresManagerAndPendingStatus() {
        assertThat(GeneralInspectionTaskService.canReassignTask("PENDING", true)).isTrue();
        assertThat(GeneralInspectionTaskService.canReassignTask("PENDING", false)).isFalse();
        assertThat(GeneralInspectionTaskService.canReassignTask("COMPLETED", true)).isFalse();
        assertThat(GeneralInspectionTaskService.canReassignTask("RECTIFICATION_PENDING", true)).isFalse();
    }

    private GeneralInspectionTask lateTask(String status) {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setStatus(status);
        task.setSubmittedTime(LocalDateTime.now());
        task.setOnTime(0);
        return task;
    }
}
