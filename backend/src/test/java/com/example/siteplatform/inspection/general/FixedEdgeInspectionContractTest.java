package com.example.siteplatform.inspection.general;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedEdgeInspectionContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260826_general_inspection_fixed_edge.sql");
    private static final Path BASELINE = Path.of(
            "src/main/resources/sql/empty-database-baseline.sql.template");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/example/siteplatform/inspection/general/controller/GeneralInspectionController.java");
    private static final Path PUBLIC_CONTROLLER = Path.of(
            "src/main/java/com/example/siteplatform/inspection/general/controller/PublicGeneralInspectionController.java");

    private static final List<String> TYPE_CODES = List.of(
            "FLOOR_BALCONY_EAVE_EDGE",
            "STAIR_PLATFORM_FLIGHT_EDGE",
            "ROOF_EDGE",
            "PIT_TRENCH_EDGE",
            "OPENING_RESERVED_HOLE",
            "ELEVATOR_SHAFT",
            "HOIST_LANDING_PLATFORM",
            "LOADING_UNLOADING_PLATFORM");

    private static final List<String> PERMISSION_CODES = List.of(
            "EDGE_INSPECTION_VIEW",
            "EDGE_INSPECTION_MANAGE",
            "EDGE_INSPECTION_SUBMIT",
            "EDGE_INSPECTION_RECTIFY",
            "EDGE_INSPECTION_REVIEW");

    @Test
    void additiveMigrationConvertsOpenConfigurationWithoutDeletingHistory() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains("20260826_GENERAL_INSPECTION_V1");
        assertThat(sql).contains("20260826_GENERAL_INSPECTION_FIXED_EDGE_V1");
        assertThat(sql).contains("point_type_code", "point_type_name", "edge_active_since_time",
                "building_name", "floor_name");
        assertThat(sql).contains("plan_code <> 'EDGE_PROJECT_SCHEDULE'");
        assertThat(sql).contains("UPDATE general_inspection_project_setting", "SET enabled = 0");
        assertThat(sql).contains("WHERE enabled <> 0");
        assertThat(sql).contains("menu_code = 'INSPECTION_EDGE'");
        assertThat(sql).contains("menu_code = 'INSPECTION_CONFIG'");
        assertThat(sql).contains("permission_code = 'CUSTOM_INSPECTION_SUBMIT'");
        assertThat(sql).doesNotContain("DELETE FROM general_inspection_");
        TYPE_CODES.forEach(code -> assertThat(sql).contains("'" + code + "'"));
        PERMISSION_CODES.forEach(code -> assertThat(sql).contains("'" + code + "'"));

        String itemSeed = sql.substring(sql.indexOf("INSERT INTO tmp_edge_template_item VALUES"),
                sql.indexOf("INSERT INTO general_inspection_template_item"));
        assertThat(itemSeed.lines().filter(line -> line.startsWith("('")).count()).isEqualTo(40);
    }

    @Test
    void emptyBaselineEndsWithTheSameFixedEdgeContract() throws IOException {
        String migration = Files.readString(MIGRATION);
        String baseline = Files.readString(BASELINE);
        for (String marker : List.of(
                "20260826_GENERAL_INSPECTION_FIXED_EDGE_V1",
                "idx_general_edge_point",
                "idx_general_edge_task",
                "EDGE_PROJECT_SCHEDULE",
                "INSPECTION_EDGE")) {
            assertThat(migration).contains(marker);
            assertThat(baseline).contains(marker);
        }
        TYPE_CODES.forEach(code -> assertThat(baseline).contains("'" + code + "'"));
        PERMISSION_CODES.forEach(code -> assertThat(baseline).contains("'" + code + "'"));
    }

    @Test
    void onlyFixedEdgeBusinessControllerRoutesRemainReachable() throws IOException {
        String controller = Files.readString(CONTROLLER);
        assertThat(controller).contains("@RequestMapping(\"/api/v1/edge-inspections\")");
        assertThat(controller).doesNotContain(
                "/api/v1/general-inspections",
                "/templates",
                "/point-categories",
                "/scan",
                "/exports");
        assertThat(PUBLIC_CONTROLLER).doesNotExist();
    }

    @Test
    void retiredExportAndReminderWorkersAreNotScheduled() throws IOException {
        String export = Files.readString(Path.of(
                "src/main/java/com/example/siteplatform/inspection/general/service/GeneralInspectionExportService.java"));
        String reminder = Files.readString(Path.of(
                "src/main/java/com/example/siteplatform/inspection/general/service/GeneralInspectionReminderEventScheduler.java"));
        assertThat(export).doesNotContain("@Scheduled");
        assertThat(reminder).doesNotContain("@Scheduled");
    }
}
