package com.example.siteplatform.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCirculationMigrationContractTest {

    private static final String MIGRATION = "sql/migrations/20260826_document_circulation.sql";
    private static final String BASELINE = "sql/empty-database-baseline.sql.template";

    @Test
    void migrationAddsVersionedCirculationTablesAndFormalDocumentFields() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("COLUMN_NAME = 'document_type'"));
        assertTrue(sql.contains("COLUMN_NAME = 'external_revision'"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_incoming_batch"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_incoming_item"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_distribution_batch"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_distribution_item"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_distribution_recipient"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_distribution_recipient_item"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_circulation_event"));
        assertTrue(sql.contains("20260826_DOCUMENT_CIRCULATION_V1"));
    }

    @Test
    void migrationAvoidsWarningsOnTheExactLegacyBaseline() throws IOException {
        String sql = resource(MIGRATION);

        assertFalse(sql.matches("(?s).*=\\s*VALUES\\s*\\(.*"));
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS sys_data_migration"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE menu_code = 'DOCUMENT_CIRCULATION'"));
        assertTrue(sql.contains("AS incoming\nON DUPLICATE KEY UPDATE permission_code = incoming.permission_code"));
        assertTrue(sql.contains("SET @create_sys_data_migration_sql = IF("));
        assertFalse(sql.contains("INSERT IGNORE INTO sys_role_menu"));
        assertFalse(sql.contains("INSERT IGNORE INTO sys_role_permission"));
        assertFalse(sql.contains("INSERT IGNORE INTO sys_data_migration"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key"));
    }

    @Test
    void migrationOnlySeedsPlatformAdminAndNeverGrantsOrdinaryRoles() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("role_code = 'PLATFORM_ADMIN'"));
        assertTrue(sql.contains("DOCUMENT_CIRCULATION"));
        assertTrue(sql.contains("document.receive"));
        assertTrue(sql.contains("document.issue"));
        assertTrue(sql.contains("document.circulation.view"));
        assertTrue(sql.contains("document.circulation.export"));
        assertFalse(sql.contains("role_code = 'PROJECT_ADMIN'"));
        assertFalse(sql.contains("role_code = 'USER'"));
    }

    @Test
    void emptyDatabaseBaselineContainsTheSameCurrentSchema() throws IOException {
        String sql = resource(BASELINE);

        assertTrue(sql.contains("document_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL'"));
        assertTrue(sql.contains("version_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT'"));
        assertTrue(sql.contains("CREATE TABLE document_circulation_event"));
        assertTrue(sql.contains("DOCUMENT_CIRCULATION"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "资源不存在：" + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
