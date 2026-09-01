package com.example.siteplatform.inspection.general;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeInspectionExportMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/sql/migrations/20260828_edge_inspection_export.sql");

    @Test
    void migrationAddsTypedJobCountersAndImmutableTaskSnapshot() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains("export_type", "point_count", "task_count", "photo_count", "photo_bytes");
        assertThat(sql).contains("general_inspection_export_job_task", "uk_general_export_job_task");
        assertThat(sql).contains("20260828_EDGE_INSPECTION_EXPORT_V1");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM general_inspection");
    }
}
