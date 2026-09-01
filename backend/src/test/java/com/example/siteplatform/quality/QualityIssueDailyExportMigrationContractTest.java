package com.example.siteplatform.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityIssueDailyExportMigrationContractTest {

    private static final String MIGRATION = "sql/migrations/20260828_quality_issue_daily_export.sql";
    private static final String BASELINE = "sql/empty-database-baseline.sql.template";

    @Test
    void migrationBackfillsStableBusinessDateAndCreatesIdempotentExportTables() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("COLUMN_NAME = 'record_date'"));
        assertTrue(sql.contains("COALESCE(qwi.inspection_date, DATE(qi.create_time))"));
        assertTrue(sql.contains("MODIFY COLUMN record_date DATE NOT NULL"));
        assertTrue(sql.contains("idx_quality_issue_project_record_date (project_id, record_date, status, deleted)"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS quality_issue_export_job"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS quality_issue_export_job_item"));
        assertTrue(sql.contains("20260828_QUALITY_ISSUE_DAILY_EXPORT_V1"));
    }

    @Test
    void emptyDatabaseBaselineContainsTheSameDailyExportContract() throws IOException {
        String sql = resource(BASELINE);

        assertTrue(sql.contains("record_date                     DATE NOT NULL"));
        assertTrue(sql.contains("idx_quality_issue_project_record_date (project_id, record_date, status, deleted)"));
        assertTrue(sql.contains("CREATE TABLE quality_issue_export_job ("));
        assertTrue(sql.contains("CREATE TABLE quality_issue_export_job_item ("));
        assertTrue(sql.contains("file_resource_id BIGINT COMMENT '生成的Excel文件ID'"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "资源不存在：" + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
