package com.example.siteplatform.system;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoleAuthorizationConsistencyMigrationContractTest {

    private static final String MIGRATION =
            "sql/migrations/20260829_role_authorization_consistency.sql";

    @Test
    void migrationOnlyRepairsModulesAndMenusWithoutGrantingOperationPermissions() throws IOException {
        String sql = resource(MIGRATION);
        String normalized = sql.toLowerCase();

        assertThat(sql).contains(
                "INSERT INTO sys_role_business_module",
                "INSERT INTO sys_role_menu",
                "ON DUPLICATE KEY UPDATE role_id = sys_role_business_module.role_id",
                "ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id",
                "20260829_ROLE_AUTHORIZATION_CONSISTENCY_V1");
        assertThat(normalized).doesNotContain(
                "insert into sys_role_permission",
                "insert ignore into sys_role_permission",
                "insert into sys_permission",
                "insert ignore into sys_permission",
                "create table",
                "alter table");
    }

    @Test
    void migrationMapsEveryUnambiguousBusinessPermissionToItsExistingPage() throws IOException {
        String sql = resource(MIGRATION);

        List<String> mappings = List.of(
                "'site_access.view' permission_code, 'SITE_VISITOR' menu_code",
                "'document.upload', 'DOCUMENT_LIBRARY'",
                "'document.receive', 'DOCUMENT_CIRCULATION'",
                "'document.issue', 'DOCUMENT_CIRCULATION'",
                "'document.circulation.view', 'DOCUMENT_CIRCULATION'",
                "'document.circulation.export', 'DOCUMENT_CIRCULATION'",
                "'seal.view', 'DOCUMENT_SEAL'",
                "'seal.manage', 'DOCUMENT_SEAL'",
                "'seal.export', 'DOCUMENT_SEAL'",
                "'BOX_VIEW', 'INSPECTION_LEDGER'",
                "'BOX_MANAGE', 'INSPECTION_LEDGER'",
                "'BOX_QR_MANAGE', 'INSPECTION_LEDGER'",
                "'BOX_PUBLIC_ACCESS', 'INSPECTION_LEDGER'",
                "'INSPECTION_DAILY_SUBMIT', 'INSPECTION_RECORDS'",
                "'INSPECTION_RECORD_VIEW', 'INSPECTION_RECORDS'",
                "'inspection.rectify', 'INSPECTION_RECTIFICATIONS'",
                "'inspection.review', 'INSPECTION_RECTIFICATIONS'",
                "'EDGE_INSPECTION_VIEW', 'INSPECTION_EDGE'",
                "'EDGE_INSPECTION_MANAGE', 'INSPECTION_EDGE'",
                "'EDGE_INSPECTION_SUBMIT', 'INSPECTION_EDGE'",
                "'EDGE_INSPECTION_RECTIFY', 'INSPECTION_EDGE'",
                "'EDGE_INSPECTION_REVIEW', 'INSPECTION_EDGE'",
                "'quality.rectify', 'QUALITY_ISSUES'",
                "'quality.review', 'QUALITY_ISSUES'");

        assertThat(mappings).allSatisfy(mapping -> assertThat(sql).contains(mapping));
        assertThat(sql).contains(
                "SELECT 'DOCUMENT', 'WEB_DOCUMENT'",
                "SELECT 'DOCUMENT', 'MINI_DOCUMENT'",
                "SELECT 'INSPECTION', 'WEB_INSPECTION'",
                "SELECT 'INSPECTION', 'MINI_INSPECTION'",
                "SELECT 'QUALITY', 'WEB_QUALITY'",
                "SELECT 'QUALITY', 'MINI_QUALITY'");
    }

    @Test
    void migrationDoesNotInventChildMenusForSharedPermissions() throws IOException {
        String sql = resource(MIGRATION);

        assertThat(sql).doesNotContain(
                "SELECT 'document.view',",
                "SELECT 'document.manage',",
                "SELECT 'inspection.view',",
                "SELECT 'inspection.manage',",
                "SELECT 'inspection.submit',",
                "SELECT 'inspection.export',",
                "SELECT 'SUMMARY_VIEW',",
                "SELECT 'SUMMARY_EXPORT',",
                "SELECT 'quality.view',",
                "SELECT 'quality.manage',");
    }

    @Test
    void migrationIsRerunnableAndCarriesForwardRetiredProjectMemberMarker() throws IOException {
        String sql = resource(MIGRATION);

        assertThat(sql).contains(
                "DELETE rm\nFROM sys_role_menu",
                "DELETE rp\nFROM sys_role_permission",
                "menu.enabled <> 1",
                "permission.enabled <> 1",
                "WHERE CAST(menu_code AS BINARY) = CAST('SYSTEM_PROJECT' AS BINARY)",
                "WHERE CAST(permission_code AS BINARY) = CAST('project.member.manage' AS BINARY)",
                "20260807_RETIRE_PROJECT_MEMBER_MANAGEMENT_PAGE_V1",
                "INSERT INTO sys_data_migration",
                "ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key");
        int cleanupStart = sql.indexOf("-- 清理仍指向不存在、停用或已删除目录/权限的关系");
        String invalidMenuCleanup = sql.substring(
                cleanupStart,
                sql.indexOf("DELETE rp\nFROM sys_role_permission", cleanupStart));
        assertThat(invalidMenuCleanup).doesNotContain("menu.visible <> 1");
        assertThat(count(sql, "INSERT INTO sys_data_migration")).isEqualTo(2);
        assertThat(count(sql, "ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key"))
                .isEqualTo(2);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "资源不存在：" + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String source, String needle) {
        int total = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            total++;
            offset += needle.length();
        }
        return total;
    }
}
