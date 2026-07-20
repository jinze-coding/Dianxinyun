package com.example.siteplatform.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionPermissionTemplateMapper extends BaseMapper<InspectionPermissionTemplate> {

    @Select("""
            SELECT *
            FROM inspection_permission_template
            WHERE template_code = #{templateCode}
              AND deleted = 0
            LIMIT 1
            """)
    InspectionPermissionTemplate selectByTemplateCode(@Param("templateCode") String templateCode);
}
