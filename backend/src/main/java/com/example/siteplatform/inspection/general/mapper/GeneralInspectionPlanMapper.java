package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeneralInspectionPlanMapper extends BaseMapper<GeneralInspectionPlan> {
    @Select("SELECT * FROM general_inspection_plan WHERE id=#{id} AND deleted=0 FOR UPDATE")
    GeneralInspectionPlan selectByIdForUpdate(@Param("id") Long id);
}
