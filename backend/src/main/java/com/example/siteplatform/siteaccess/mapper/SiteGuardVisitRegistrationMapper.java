package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitRegistration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SiteGuardVisitRegistrationMapper extends BaseMapper<SiteGuardVisitRegistration> {
    @Select("SELECT * FROM site_guard_visit_registration WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteGuardVisitRegistration selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM site_guard_visit_registration
            WHERE project_id = #{projectId} AND wechat_app_id = #{appId}
              AND visitor_identity_hash = #{identityHash} AND status = 'REGISTERED'
              AND valid_until > #{now} AND deleted = 0
            ORDER BY registered_time DESC, id DESC LIMIT 1 FOR UPDATE
            """)
    SiteGuardVisitRegistration selectActiveForUpdate(@Param("projectId") Long projectId,
                                                      @Param("appId") String appId,
                                                      @Param("identityHash") String identityHash,
                                                      @Param("now") LocalDateTime now);
}
