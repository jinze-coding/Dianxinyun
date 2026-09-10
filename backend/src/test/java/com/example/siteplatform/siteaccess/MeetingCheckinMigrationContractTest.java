package com.example.siteplatform.siteaccess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingCheckinMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260902_site_access_meeting_checkin.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");

    @Test
    void migrationIsIdempotentAndBackfillsOnlyRegistrationSource() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "information_schema.COLUMNS",
                "registration_source VARCHAR(20) NOT NULL DEFAULT ''INVITATION''",
                "SET registration_source = 'INVITATION'",
                "CREATE TABLE IF NOT EXISTS site_meeting_checkin_qr",
                "CREATE TABLE IF NOT EXISTS site_meeting_attendance",
                "20260902_SITE_ACCESS_MEETING_CHECKIN_V1");
        assertThat(sql).doesNotContain("INSERT INTO site_meeting_attendance");
    }

    @Test
    void qrAndAttendanceSchemaKeepOpaqueSceneAndNoVisitorCoordinates() throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains(
                "scene_token_hash char(64) not null",
                "scene_token_encrypted varchar(512) not null",
                "unique key uk_site_meeting_checkin_current_invitation",
                "unique key uk_site_meeting_attendance_person",
                "distance_meters int",
                "accuracy_meters int",
                "reference_project_version int");
        assertThat(sql).doesNotContain(
                "visitor_latitude", "visitor_longitude", "checkin_latitude", "checkin_longitude");
    }

    @Test
    void emptyDatabaseBaselineContainsSameCheckinDomain() throws IOException {
        String sql = Files.readString(BASELINE);

        assertThat(sql).contains(
                "registration_source VARCHAR(20) NOT NULL DEFAULT 'INVITATION'",
                "CREATE TABLE site_meeting_checkin_qr",
                "CREATE TABLE site_meeting_attendance",
                "uk_site_meeting_checkin_current_invitation",
                "uk_site_meeting_attendance_person",
                "20260902_SITE_ACCESS_MEETING_CHECKIN_V1");
    }
}
