package com.example.siteplatform.siteaccess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingVisitMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260826_site_access_meeting_invitation.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");

    @Test
    void migrationKeepsExistingInvitationsSingleAndCreatesMeetingRegistrationDomain() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "ADD COLUMN invite_type VARCHAR(20) NOT NULL DEFAULT ''SINGLE''",
                "UPDATE site_visit_invitation SET invite_type = 'SINGLE'",
                "CREATE TABLE IF NOT EXISTS site_meeting_visit_registration",
                "CREATE TABLE IF NOT EXISTS site_meeting_visit_person",
                "CREATE TABLE IF NOT EXISTS site_meeting_visit_audit_log",
                "20260826_SITE_ACCESS_MEETING_INVITATION_V1");
    }

    @Test
    void oneActiveRegistrationPerWechatIdentityAllowsVoidedHistory() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "visitor_identity_hash CHAR(64) NOT NULL",
                "CASE WHEN deleted = 0 AND status = 'REGISTERED'",
                "CONCAT(invitation_id, ':', wechat_app_id, ':', visitor_identity_hash)",
                "UNIQUE KEY uk_site_meeting_visit_active_identity (active_identity_key)");
        assertThat(sql.toLowerCase()).doesNotContain(
                "openid varchar", "open_id varchar", "unionid varchar", "union_id varchar");
    }

    @Test
    void baselineContainsTheSameMeetingSchemaAndMigrationMarker() throws IOException {
        String sql = Files.readString(BASELINE);

        assertThat(sql).contains(
                "invite_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE'",
                "CREATE TABLE site_meeting_visit_registration",
                "CREATE TABLE site_meeting_visit_person",
                "CREATE TABLE site_meeting_visit_audit_log",
                "uk_site_meeting_visit_active_identity",
                "20260826_SITE_ACCESS_MEETING_INVITATION_V1");
    }
}
