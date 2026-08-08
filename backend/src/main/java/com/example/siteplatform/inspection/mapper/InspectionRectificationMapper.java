package com.example.siteplatform.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionRectificationMapper extends BaseMapper<InspectionRectification> {

    @Select("""
            SELECT *
            FROM inspection_rectification
            WHERE id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    InspectionRectification selectByIdForUpdate(@Param("id") Long id);
}
