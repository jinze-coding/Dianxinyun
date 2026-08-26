package com.example.siteplatform.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {

    @Select("""
            SELECT *
            FROM inspection_record
            WHERE id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    InspectionRecord selectByIdForUpdate(@Param("id") Long id);
}
