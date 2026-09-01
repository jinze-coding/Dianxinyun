package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionExportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeneralInspectionExportJobMapper extends BaseMapper<GeneralInspectionExportJob> {
    @Select("SELECT * FROM general_inspection_export_job WHERE id=#{id} FOR UPDATE")
    GeneralInspectionExportJob selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM general_inspection_export_job
            WHERE export_type = 'EDGE' AND status = 'PENDING'
            ORDER BY create_time ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    GeneralInspectionExportJob selectNextPendingEdgeForUpdate();
}
