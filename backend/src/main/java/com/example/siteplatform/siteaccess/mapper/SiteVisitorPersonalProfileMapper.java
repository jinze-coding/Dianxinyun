package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorPersonalProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SiteVisitorPersonalProfileMapper extends BaseMapper<SiteVisitorPersonalProfile> {
    @Select("SELECT * FROM site_visitor_personal_profile WHERE project_id=#{projectId} AND wechat_app_id=#{appId} AND owner_identity_hash=#{identity} AND deleted=0 FOR UPDATE")
    SiteVisitorPersonalProfile selectOwnerForUpdate(@Param("projectId") Long projectId,
            @Param("appId") String appId, @Param("identity") String identity);

    @Insert("INSERT INTO site_visitor_personal_profile_audit (profile_id,project_id,action,before_encrypted,after_encrypted,create_time) VALUES (#{profileId},#{projectId},#{action},#{before},#{after},CURRENT_TIMESTAMP)")
    int insertAudit(@Param("profileId") Long profileId, @Param("projectId") Long projectId,
            @Param("action") String action, @Param("before") String before, @Param("after") String after);
}
