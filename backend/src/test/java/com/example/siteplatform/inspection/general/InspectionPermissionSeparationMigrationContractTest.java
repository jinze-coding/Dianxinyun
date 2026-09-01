package com.example.siteplatform.inspection.general;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionPermissionSeparationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260829_inspection_permission_separation.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");
    private static final Path DEMO_SCRIPT = Path.of(
            "../scripts/edge-inspection-demo-data.sh");

    private static final List<String> EDGE_PERMISSIONS = List.of(
            "EDGE_INSPECTION_VIEW",
            "EDGE_INSPECTION_MANAGE",
            "EDGE_INSPECTION_SUBMIT",
            "EDGE_INSPECTION_RECTIFY",
            "EDGE_INSPECTION_REVIEW",
            "EDGE_INSPECTION_EXPORT");

    @Test
    void migrationAddsDedicatedEdgeExportAndKeepsElectricPermissionCodesStable() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "20260829_INSPECTION_PERMISSION_SEPARATION_V1",
                "'EDGE_INSPECTION_EXPORT', '导出临边巡检含图报表'",
                "description = '创建、查看并下载临边巡检含图 Excel 导出任务'",
                "enabled = 1",
                "builtin = 1",
                "role.role_code = 'PLATFORM_ADMIN'",
                "role.scope_type = 'PLATFORM'");

        assertThat(List.of(
                "'inspection.view' code, '查看电箱巡检'",
                "'inspection.manage', '管理电箱巡检'",
                "'inspection.submit', '提交电箱日检'",
                "'inspection.rectify', '提交电箱巡检整改'",
                "'inspection.review', '复查电箱巡检整改'",
                "'inspection.export', '导出电箱巡检报表'",
                "'INSPECTION_RECORD_VIEW', '查看电箱巡检记录'",
                "'SUMMARY_VIEW', '查看电箱巡检汇总'",
                "'SUMMARY_EXPORT', '导出电箱巡检报表'"))
                .allSatisfy(seed -> assertThat(sql).contains(seed));

        assertThat(sql.toLowerCase()).doesNotContain(
                "update sys_role_permission",
                "replace into sys_role_permission");
    }

    @Test
    void migrationIsMarkerGuardedAndContainsNoSchemaOrRoleTemplateReset() throws IOException {
        String sql = Files.readString(MIGRATION);
        String normalized = sql.toLowerCase();

        assertThat(sql).contains(
                "SET @inspection_permission_separation_required :=",
                "WHERE migration_key = '20260829_INSPECTION_PERMISSION_SEPARATION_V1'",
                "SELECT '20260829_INSPECTION_PERMISSION_SEPARATION_V1'",
                "WHERE @inspection_permission_separation_required = 1",
                "START TRANSACTION",
                "COMMIT");
        assertThat(count(sql, "@inspection_permission_separation_required = 1"))
                .isGreaterThanOrEqualTo(12);
        assertThat(normalized).doesNotContain(
                "create table",
                "alter table",
                "drop table",
                "update sys_role set",
                "update inspection_permission_template",
                "delete from inspection_permission_template");
    }

    @Test
    void legacyEdgeExportIsConvertedBeforeOldElectricDependenciesAreConsideredForCleanup() throws IOException {
        String sql = Files.readString(MIGRATION);
        int compatibilityGrant = sql.indexOf("-- 等价迁移旧临边导出组合");
        int cleanup = sql.indexOf("-- 以下清理只处理已经持有任一正式临边权限的普通角色");

        assertThat(compatibilityGrant).isGreaterThanOrEqualTo(0);
        assertThat(cleanup).isGreaterThan(compatibilityGrant);
        String compatibilityBlock = sql.substring(compatibilityGrant, cleanup);
        assertThat(compatibilityBlock).contains(
                "@edge_view_permission_id",
                "@summary_export_permission_id",
                "@inspection_export_permission_id",
                "@edge_export_permission_id",
                "INSERT INTO sys_role_permission",
                "ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id");
    }

    @Test
    void everyEdgeCapabilityGetsInspectionModuleAndTheThreeExistingMenus() throws IOException {
        String sql = Files.readString(MIGRATION);

        EDGE_PERMISSIONS.forEach(code ->
                assertThat(count(sql, "'" + code + "'")).isGreaterThanOrEqualTo(2));
        assertThat(sql).contains(
                "INSERT INTO sys_role_business_module(role_id, module_code)",
                "INSERT INTO sys_role_menu(role_id, menu_id)",
                "ON DUPLICATE KEY UPDATE role_id = sys_role_business_module.role_id",
                "ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id",
                "menu.menu_code IN ('WEB_INSPECTION', 'MINI_INSPECTION', 'INSPECTION_EDGE')");
    }

    @Test
    void menuDictionaryNamesElectricRecordsAndRectificationsWithoutChangingCodesOrRoutes() throws IOException {
        String sql = Files.readString(MIGRATION);
        String baseline = Files.readString(BASELINE);
        int blockStart = sql.indexOf("-- 菜单字典也按专区收口");
        int blockEnd = sql.indexOf("SET @edge_view_permission_id", blockStart);

        assertThat(blockStart).isGreaterThanOrEqualTo(0);
        String menuUpdate = sql.substring(blockStart, blockEnd);
        assertThat(menuUpdate).contains(
                "WHEN 'INSPECTION_RECORDS' THEN '电箱巡检记录'",
                "WHEN 'INSPECTION_RECTIFICATIONS' THEN '电箱整改闭环'");
        assertThat(menuUpdate).doesNotContain("route_path =", "permission_code =", "menu_code =");

        assertThat(baseline).contains(
                "'INSPECTION_RECORDS', '电箱巡检记录', 'TAB'",
                "'INSPECTION_RECORDS', 'inspection.view', 22",
                "'INSPECTION_RECTIFICATIONS', '电箱整改闭环', 'TAB'",
                "'INSPECTION_RECTIFICATIONS', 'inspection.view', 23");
    }

    @Test
    void cleanupIsCapabilityBasedAndNeverResetsRolesByJob() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "CAST(target_permission.permission_code AS BINARY) = CAST('inspection.manage' AS BINARY)",
                "box_manage_role_permission.id IS NULL",
                "CAST(target_permission.permission_code AS BINARY) = CAST('inspection.submit' AS BINARY)",
                "daily_submit_role_permission.id IS NULL",
                "target_permission.permission_code IN ('SUMMARY_VIEW', 'SUMMARY_EXPORT')",
                "records_role_menu.id IS NULL",
                "CAST(target_permission.permission_code AS BINARY) = CAST('inspection.export' AS BINARY)",
                "summary_export_role_permission.id IS NULL",
                "target_permission.permission_code IN ('inspection.rectify', 'inspection.review')",
                "rectifications_role_menu.id IS NULL",
                "CAST(target_permission.permission_code AS BINARY) = CAST('inspection.view' AS BINARY)",
                "box_view_role_permission.id IS NULL",
                "record_view_role_permission.id IS NULL",
                "rectify_role_permission.id IS NULL",
                "review_role_permission.id IS NULL");

        assertThat(count(sql, "DELETE target_role_permission")).isEqualTo(6);
        assertThat(count(sql,
                "NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')"))
                .isEqualTo(6);
        assertThat(sql).doesNotContain(
                "role.role_code = 'ELECTRICIAN'",
                "role.role_code = 'SAFETY_OFFICER'",
                "role.role_code = 'PROJECT_ADMIN'",
                "role.role_code = 'USER'",
                "target_permission.permission_code IN ('BOX_",
                "CAST(target_permission.permission_code AS BINARY) = CAST('BOX_");
    }

    @Test
    void baselineAndDemoSeedUseTheSixPermissionContractWithoutGrantingEdgeToElectricTemplates() throws IOException {
        String baseline = Files.readString(BASELINE);
        String demo = Files.readString(DEMO_SCRIPT);

        assertThat(baseline).contains(
                "'EDGE_INSPECTION_EXPORT', '导出临边巡检含图报表'",
                "20260829_INSPECTION_PERMISSION_SEPARATION_V1",
                "查看电箱巡检记录",
                "查看电箱巡检汇总",
                "导出电箱巡检报表",
                "临边巡检权限独立配置，不写入历史电箱模板");
        String electricTemplateSeed = baseline.substring(
                baseline.indexOf("INSERT INTO inspection_permission_template"),
                baseline.indexOf("-- 关联管理员项目权限"));
        assertThat(electricTemplateSeed).doesNotContain("EDGE_INSPECTION_");

        assertThat(demo).contains(
                "edge_permission_count",
                "[ \"$edge_permission_count\" = \"6\" ]",
                "'EDGE_INSPECTION_REVIEW','EDGE_INSPECTION_EXPORT'",
                "role.role_code='SAFETY_OFFICER'",
                "permission.permission_code='EDGE_INSPECTION_EXPORT'");

        int demoGrantStart = demo.indexOf("INSERT IGNORE INTO sys_role_permission(role_id, permission_id)");
        int demoGrantEnd = demo.indexOf("-- 演示脚本只补临边专属操作权限", demoGrantStart);
        String demoRoleGrants = demo.substring(demoGrantStart, demoGrantEnd);
        assertThat(count(demoRoleGrants,
                "INSERT IGNORE INTO sys_role_permission(role_id, permission_id)"))
                .isEqualTo(2);
        assertThat(demoRoleGrants).contains(
                "'EDGE_INSPECTION_VIEW','EDGE_INSPECTION_SUBMIT','EDGE_INSPECTION_RECTIFY'",
                "'EDGE_INSPECTION_VIEW','EDGE_INSPECTION_MANAGE'",
                "'EDGE_INSPECTION_REVIEW','EDGE_INSPECTION_EXPORT'");
        assertThat(demoRoleGrants).doesNotContain(
                "'inspection.",
                "'SUMMARY_",
                "'BOX_",
                "'INSPECTION_DAILY_SUBMIT'",
                "'INSPECTION_RECORD_VIEW'");
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
