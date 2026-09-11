package com.example.siteplatform.seal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.seal.entity.SealFormExportJob;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SealFormExportJobMapper extends BaseMapper<SealFormExportJob> {
    @Select("SELECT COUNT(*) FROM sys_data_migration WHERE migration_key = '20260911_SEAL_FORM_MERGE_EXPORT_V1'")
    int migrationApplied();

    @Select("SELECT * FROM seal_form_export_job WHERE id = #{id} FOR UPDATE")
    SealFormExportJob lock(@Param("id") Long id);

    @Select("""
        SELECT * FROM seal_form_export_job
        WHERE status = 'PENDING' OR (status = 'RUNNING' AND lease_until < NOW())
        ORDER BY create_time, id LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    SealFormExportJob nextForUpdate();
}
