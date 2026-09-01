package com.example.siteplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.quality.entity.QualityIssueExportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QualityIssueExportJobMapper extends BaseMapper<QualityIssueExportJob> {

    @Select("""
            SELECT COUNT(*) FROM sys_data_migration
            WHERE migration_key = '20260828_QUALITY_ISSUE_DAILY_EXPORT_V1'
            """)
    int countAppliedMigration();

    @Select("SELECT * FROM quality_issue_export_job WHERE id = #{id} FOR UPDATE")
    QualityIssueExportJob selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM quality_issue_export_job
            WHERE status = 'PENDING'
            ORDER BY create_time ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    QualityIssueExportJob selectNextPendingForUpdate();
}
