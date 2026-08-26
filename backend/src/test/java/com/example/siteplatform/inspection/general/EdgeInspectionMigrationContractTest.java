package com.example.siteplatform.inspection.general;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeInspectionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260826_general_inspection_fixed_edge.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");

    @Test
    void migrationSeedsOnlyTheEightFixedPointTypesAndFivePermissions() throws IOException {
        String sql = Files.readString(MIGRATION);
        List<String> pointTypes = List.of(
                "FLOOR_BALCONY_EAVE_EDGE",
                "STAIR_PLATFORM_FLIGHT_EDGE",
                "ROOF_EDGE",
                "PIT_TRENCH_EDGE",
                "OPENING_RESERVED_HOLE",
                "ELEVATOR_SHAFT",
                "HOIST_LANDING_PLATFORM",
                "LOADING_UNLOADING_PLATFORM");
        List<String> permissions = List.of(
                "EDGE_INSPECTION_VIEW",
                "EDGE_INSPECTION_MANAGE",
                "EDGE_INSPECTION_SUBMIT",
                "EDGE_INSPECTION_RECTIFY",
                "EDGE_INSPECTION_REVIEW");

        assertThat(pointTypes).allSatisfy(code -> assertThat(sql).contains("'" + code + "'"));
        assertThat(permissions).allSatisfy(code -> assertThat(sql).contains("'" + code + "'"));
        assertThat(sql).contains(
                "'INSPECTION_EDGE', '临边巡检'",
                "20260826_GENERAL_INSPECTION_FIXED_EDGE_V1",
                "edge_generation_lower_bound_time",
                "UPDATE general_inspection_project_setting",
                "WHERE enabled <> 0 AND @fixed_edge_already_applied = 0");
        assertThat(count(sql, "INSERT INTO tmp_edge_template_item VALUES")).isEqualTo(1);
        assertThat(count(sql, "重大事故隐患判定标准（2024版）")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rerunGuardsAndCollationSafeComparisonsAreKeptInMigrationAndBaseline() throws IOException {
        String migration = Files.readString(MIGRATION);
        String baseline = Files.readString(BASELINE);

        for (String sql : List.of(migration, baseline)) {
            assertThat(sql).contains(
                    "edge_generation_lower_bound_time",
                    "SET edge_generation_lower_bound_time = COALESCE(create_time, CURRENT_TIMESTAMP)",
                    "SET @fixed_edge_already_applied :=",
                    "BINARY category.category_code = BINARY seed.type_code",
                    "BINARY template.template_code = BINARY CONCAT('EDGE_', seed.type_code)",
                    "BINARY item.item_key = BINARY seed.item_key",
                    "SELECT 1 FROM sys_menu WHERE BINARY menu_code = BINARY 'INSPECTION_EDGE'",
                    "WHERE menu_code = 'INSPECTION_EDGE' AND @fixed_edge_already_applied = 0",
                    "WHERE @fixed_edge_already_applied = 0\n  AND @platform_admin_role_id IS NOT NULL");
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
