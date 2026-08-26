package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeneralInspectionRectificationMapper extends BaseMapper<GeneralInspectionRectification> {
    @Select("SELECT * FROM general_inspection_rectification WHERE id=#{id} FOR UPDATE")
    GeneralInspectionRectification selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM general_inspection_rectification WHERE task_id=#{taskId} ORDER BY id FOR UPDATE")
    List<GeneralInspectionRectification> selectByTaskIdForUpdate(@Param("taskId") Long taskId);
}
