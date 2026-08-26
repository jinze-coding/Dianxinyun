package com.example.siteplatform.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityWeeklyMigrationContractTest {

    private static final String MIGRATION = "sql/migrations/20260826_quality_weekly_inspection.sql";
    private static final String BASELINE = "sql/empty-database-baseline.sql.template";

    @Test
    void migrationIsIdempotentAndAddsWeeklyOwnershipWithoutRecreatingMenus() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS quality_weekly_inspection"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS quality_weekly_inspection_draft_item"));
        assertTrue(sql.contains("project_id BIGINT NOT NULL"));
        assertTrue(sql.contains("uk_quality_weekly_project_week (project_id, week_start)"));
        assertTrue(sql.contains("COLUMN_NAME = 'weekly_inspection_id'"));
        assertTrue(sql.contains("INDEX_NAME = 'idx_quality_issue_weekly'"));
        assertTrue(sql.contains("20260826_QUALITY_WEEKLY_INSPECTION_V1"));
        assertFalse(Pattern.compile("(?is)INSERT\\s+(?:IGNORE\\s+)?INTO\\s+sys_menu")
                .matcher(sql).find());
    }

    @Test
    void migrationAndBaselineOnlyRenameEnabledFormalQualityMenus() throws IOException {
        for (String sql : new String[]{resource(MIGRATION), resource(BASELINE)}) {
            assertSafeRename(sql, "WEB_QUALITY", "质量周检");
            assertSafeRename(sql, "MINI_QUALITY", "质量周检");
            assertSafeRename(sql, "QUALITY_ISSUES", "周检记录");
        }
    }

    private void assertSafeRename(String sql, String menuCode, String menuName) {
        Pattern update = Pattern.compile(
                "(?is)UPDATE\\s+sys_menu\\s+SET\\s+menu_name\\s*=\\s*'"
                        + Pattern.quote(menuName)
                        + "'\\s+WHERE\\s+menu_code\\s*=\\s*'"
                        + Pattern.quote(menuCode)
                        + "'\\s+AND\\s+enabled\\s*=\\s*1\\s+AND\\s+deleted\\s*=\\s*0");
        assertTrue(update.matcher(sql).find(), "缺少安全菜单重命名：" + menuCode);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "资源不存在：" + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
