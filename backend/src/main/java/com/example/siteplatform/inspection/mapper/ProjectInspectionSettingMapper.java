package com.example.siteplatform.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectInspectionSettingMapper extends BaseMapper<ProjectInspectionSetting> {
    @Select("SELECT * FROM project_inspection_setting WHERE project_id = #{projectId} LIMIT 1 FOR UPDATE")
    ProjectInspectionSetting selectByProjectIdForUpdate(@Param("projectId") Long projectId);
}
