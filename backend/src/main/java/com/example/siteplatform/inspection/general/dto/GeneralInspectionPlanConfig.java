package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class GeneralInspectionPlanConfig {
    @NotBlank private String frequency;
    private List<Integer> weekdays;
    private List<Integer> monthDays;
    @NotNull private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    @Min(0) @Max(1440) private Integer earlyMinutes;
    private Long assigneeId;
    private List<Long> backupAssigneeIds;
    private Long defaultRectifierId;
    @Min(0) @Max(365) private Integer rectificationDays;
    private Long reviewerId;
    private List<Long> backupReviewerIds;
    /** Internal station reminder switch; absent in historical snapshots means disabled. */
    private Boolean submissionReminderEnabled;
    /** The switch activation time. Tasks due at or before this boundary are never backfilled. */
    private LocalDateTime reminderEffectiveTime;
    @Valid @NotEmpty @Size(max = 12) private List<Slot> slots;
    @Valid @NotEmpty @Size(max = 500) private List<PointAssignment> points;

    @Data
    public static class Slot {
        private String slotCode;
        @NotBlank private String slotName;
        @NotNull private LocalTime startTime;
        @NotNull private LocalTime dueTime;
        @Min(0) @Max(1) private Integer dueDayOffset;
    }

    @Data
    public static class PointAssignment {
        @NotNull private Long pointId;
        private Long assigneeId;
        private List<Long> backupAssigneeIds;
        private Long defaultRectifierId;
        @Min(0) @Max(365) private Integer rectificationDays;
        private Long reviewerId;
        private List<Long> backupReviewerIds;
    }
}
