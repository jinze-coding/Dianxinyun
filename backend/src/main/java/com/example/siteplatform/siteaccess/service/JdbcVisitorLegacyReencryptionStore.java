package com.example.siteplatform.siteaccess.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JdbcVisitorLegacyReencryptionStore implements VisitorLegacyReencryptionStore {
    private final JdbcTemplate jdbc;

    JdbcVisitorLegacyReencryptionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void verifyEncryptedColumnWhitelist() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name LIKE 'site\\_%' ESCAPE '\\\\'
                  AND column_name LIKE '%\\_encrypted' ESCAPE '\\\\'
                ORDER BY table_name, column_name
                """);
        Set<String> actual = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            actual.add(row.get("table_name") + "." + row.get("column_name"));
        }
        Set<String> expected = VisitorLegacyReencryptionTool.encryptedColumnWhitelist();
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(expected);
            throw new IllegalStateException("访客密文字段白名单不一致，缺失=" + missing + "，未登记=" + unexpected);
        }
    }

    @Override
    public boolean migrationMarkerExists(boolean lock) {
        String sql = "SELECT migration_key FROM sys_data_migration WHERE migration_key = ?"
                + (lock ? " FOR UPDATE" : "");
        return !jdbc.queryForList(sql, String.class, VisitorLegacyReencryptionTool.MIGRATION_KEY).isEmpty();
    }

    @Override
    public List<VisitorLegacyReencryptionTool.EncryptedCell> loadEncryptedCells(boolean lock) {
        List<VisitorLegacyReencryptionTool.EncryptedCell> cells = new ArrayList<>();
        for (VisitorLegacyReencryptionTool.FieldSpec spec : VisitorLegacyReencryptionTool.FIELD_SPECS) {
            String digestSelect = spec.digestColumn() == null
                    ? "NULL AS expected_digest"
                    : spec.digestColumn() + " AS expected_digest";
            String sql = "SELECT id, " + spec.column() + " AS ciphertext, " + digestSelect
                    + " FROM " + spec.table()
                    + " WHERE " + spec.column() + " IS NOT NULL ORDER BY id"
                    + (lock ? " FOR UPDATE" : "");
            jdbc.query(sql, resultSet -> {
                cells.add(new VisitorLegacyReencryptionTool.EncryptedCell(
                        spec,
                        resultSet.getLong("id"),
                        resultSet.getString("ciphertext"),
                        resultSet.getString("expected_digest")));
            });
        }
        return cells;
    }

    @Override
    public int replaceCiphertext(VisitorLegacyReencryptionTool.EncryptedCell cell, String replacement) {
        VisitorLegacyReencryptionTool.FieldSpec spec = cell.spec();
        String preserveTimestamp = spec.hasUpdateTime() ? ", update_time = update_time" : "";
        String sql = "UPDATE " + spec.table() + " SET " + spec.column() + " = ?"
                + preserveTimestamp + " WHERE id = ? AND " + spec.column() + " = ?";
        return jdbc.update(sql, replacement, cell.id(), cell.ciphertext());
    }

    @Override
    public void insertMigrationMarker() {
        int affected = jdbc.update(
                "INSERT INTO sys_data_migration(migration_key) VALUES (?)",
                VisitorLegacyReencryptionTool.MIGRATION_KEY);
        if (affected != 1) {
            throw new IllegalStateException("访客历史密钥迁移标记写入未生效");
        }
    }
}
