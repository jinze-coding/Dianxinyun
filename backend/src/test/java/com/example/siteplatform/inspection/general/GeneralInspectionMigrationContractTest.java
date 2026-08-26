package com.example.siteplatform.inspection.general;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralInspectionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260826_general_inspection.sql");
    private static final Path EMPTY_BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");

    @Test
    void migrationContainsCompleteDefaultOffVersionedDomainAndIdempotentSeed() throws IOException {
        String sql = Files.readString(MIGRATION);
        List<String> tables = List.of(
                "general_inspection_project_setting",
                "general_inspection_template",
                "general_inspection_template_version",
                "general_inspection_template_item",
                "general_inspection_point_category",
                "general_inspection_point",
                "general_inspection_plan",
                "general_inspection_plan_version",
                "general_inspection_task",
                "general_inspection_task_item",
                "general_inspection_rectification",
                "general_inspection_action_log",
                "general_inspection_export_job",
                "general_inspection_event_outbox");

        assertThat(sql).contains("enabled TINYINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("uk_general_task_occurrence");
        assertThat(sql).contains("uk_general_event_key");
        assertThat(sql).contains("CUSTOM_INSPECTION_SUBMIT");
        assertThat(sql).contains("INSPECTION_CONFIG");
        assertThat(sql).contains("20260826_GENERAL_INSPECTION_V1");
        assertThat(sql).contains("临边通用参考模板", "必须结合项目施工方案审核");
        assertThat(sql).contains("楼层边", "楼梯边", "屋面边", "基坑沟槽", "洞口", "接料平台");
        assertThat(sql).contains("WHERE role_code = 'PLATFORM_ADMIN' AND deleted = 0");
        assertThat(sql).doesNotContain("WHERE role_code = 'PROJECT_ADMIN'");
        for (String table : tables) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(count(sql, "CREATE TABLE IF NOT EXISTS general_inspection_")).isEqualTo(tables.size());
    }

    @Test
    void emptyDatabaseBaselineContainsTheSameGeneralInspectionContract() throws IOException {
        String migration = Files.readString(MIGRATION);
        String baseline = Files.readString(EMPTY_BASELINE);
        for (String marker : List.of(
                "general_inspection_project_setting",
                "general_inspection_template_version",
                "general_inspection_plan_version",
                "uk_general_task_occurrence",
                "general_inspection_event_outbox",
                "EDGE_REFERENCE",
                "20260826_GENERAL_INSPECTION_V1")) {
            assertThat(migration).contains(marker);
            assertThat(baseline).contains(marker);
        }
    }

    private long count(String value, String token) {
        return value.lines().filter(line -> line.contains(token)).count();
    }
}
