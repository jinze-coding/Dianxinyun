package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeneralInspectionPointMapper extends BaseMapper<GeneralInspectionPoint> {
    @Select("SELECT * FROM general_inspection_point WHERE id=#{id} AND deleted=0 FOR UPDATE")
    GeneralInspectionPoint selectByIdForUpdate(@Param("id") Long id);
}
