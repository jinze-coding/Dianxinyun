package com.example.siteplatform.inspection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionSubmissionReminderMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260829_inspection_submission_reminder.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");

    @Test
    void migrationAddsDefaultOffQualityAndElectricReminderSettingsIdempotently() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS quality_weekly_reminder_setting",
                "enabled TINYINT NOT NULL DEFAULT 0",
                "UNIQUE KEY uk_quality_weekly_reminder_project (project_id)",
                "COLUMN_NAME = 'submission_reminder_enabled'",
                "COLUMN_NAME = 'reminder_effective_time'",
                "COLUMN_NAME = 'version'",
                "information_schema.STATISTICS",
                "20260829_INSPECTION_SUBMISSION_REMINDER_V1");
        assertThat(sql).doesNotContain(
                "WechatNotificationService",
                "general_inspection_event_outbox",
                "UPDATE quality_weekly_reminder_setting SET enabled = 1",
                "UPDATE project_inspection_setting SET submission_reminder_enabled = 1");
    }

    @Test
    void emptyDatabaseBaselineAlreadyContainsTheFinalReminderSchema() throws Exception {
        String sql = Files.readString(BASELINE);

        assertThat(sql).contains(
                "CREATE TABLE quality_weekly_reminder_setting",
                "submission_reminder_enabled TINYINT NOT NULL DEFAULT 0",
                "reminder_effective_time DATETIME",
                "idx_project_inspection_reminder",
                "20260829_INSPECTION_SUBMISSION_REMINDER_V1");
    }
}
