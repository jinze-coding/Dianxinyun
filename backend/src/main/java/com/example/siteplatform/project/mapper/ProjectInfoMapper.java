package com.example.siteplatform.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectInfoMapper extends BaseMapper<ProjectInfo> {
    @Select("SELECT * FROM project_info WHERE id = #{projectId} FOR UPDATE")
    ProjectInfo selectByIdForUpdate(@Param("projectId") Long projectId);
}
