package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeneralInspectionTaskMapper extends BaseMapper<GeneralInspectionTask> {
    @Select("SELECT * FROM general_inspection_task WHERE id=#{id} FOR UPDATE")
    GeneralInspectionTask selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM general_inspection_task WHERE point_id=#{pointId} AND status='PENDING' FOR UPDATE")
    List<GeneralInspectionTask> selectPendingByPointForUpdate(@Param("pointId") Long pointId);
}
