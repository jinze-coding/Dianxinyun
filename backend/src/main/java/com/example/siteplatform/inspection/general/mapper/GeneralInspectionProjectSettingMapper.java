package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GeneralInspectionProjectSettingMapper extends BaseMapper<GeneralInspectionProjectSetting> {
    @Select("""
            SELECT id FROM sys_role
            WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM' AND deleted = 0
            ORDER BY id LIMIT 1 FOR UPDATE
            """)
    Long lockPilotGuard();

    @Update("""
            UPDATE general_inspection_project_setting
            SET enabled=#{enabled}, updated_by_id=#{userId}, updated_by_name=#{userName},
                version=version+1, update_time=CURRENT_TIMESTAMP
            WHERE project_id=#{projectId} AND version=#{expectedVersion}
            """)
    int updateFeature(@Param("projectId") Long projectId, @Param("enabled") Integer enabled,
                      @Param("expectedVersion") Integer expectedVersion, @Param("userId") Long userId,
                      @Param("userName") String userName);
}
