package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SiteVisitorProfileMapper extends BaseMapper<SiteVisitorProfile> {
    @Select("SELECT * FROM site_visitor_profile WHERE profile_code = #{profileCode} AND deleted = 0 LIMIT 1 FOR UPDATE")
    SiteVisitorProfile selectForUpdateByCode(@Param("profileCode") String profileCode);

    @Select("""
            SELECT * FROM site_visitor_profile
            WHERE project_id = #{projectId}
              AND wechat_app_id = #{appId}
              AND owner_openid_hash = #{ownerHash}
              AND status = 'ACTIVE' AND deleted = 0
            FOR UPDATE
            """)
    List<SiteVisitorProfile> selectActiveOwnerProfilesForUpdate(
            @Param("projectId") Long projectId,
            @Param("appId") String appId,
            @Param("ownerHash") String ownerHash);
}
