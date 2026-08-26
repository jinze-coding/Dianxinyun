package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeneralInspectionRectificationMapper extends BaseMapper<GeneralInspectionRectification> {
    @Select("SELECT * FROM general_inspection_rectification WHERE id=#{id} FOR UPDATE")
    GeneralInspectionRectification selectByIdForUpdate(@Param("id") Long id);
}
