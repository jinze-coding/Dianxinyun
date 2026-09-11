package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorPersonalProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SiteVisitorPersonalProfileMapper extends BaseMapper<SiteVisitorPersonalProfile> {
    @Select("SELECT * FROM site_visitor_personal_profile WHERE project_id=#{projectId} AND wechat_app_id=#{appId} AND owner_identity_hash=#{identity} AND deleted=0 FOR UPDATE")
    SiteVisitorPersonalProfile selectOwnerForUpdate(@Param("projectId") Long projectId,
            @Param("appId") String appId, @Param("identity") String identity);

    // 仅在服务端验证过的微信短会话内调用；跨项目只投影本人五项信息。
    @Select("""
        SELECT visitor_company, contact_name, contact_phone_encrypted, travel_mode, vehicle_plate
        FROM (
          (SELECT v.visitor_company, v.contact_name, v.contact_phone_encrypted, v.travel_mode, v.vehicle_plate,
                  v.last_submitted_time AS submitted_time, 4 AS source_order, v.id AS source_id
           FROM site_visitor_personal_profile v JOIN project_info p ON p.id=v.project_id AND p.deleted=0
           WHERE v.wechat_app_id=#{appId} AND v.owner_identity_hash=#{personalHash} AND v.deleted=0
             AND v.last_submitted_time IS NOT NULL AND v.contact_phone_encrypted IS NOT NULL
           ORDER BY v.last_submitted_time DESC, v.id DESC LIMIT 1)
          UNION ALL
          (SELECT v.visitor_company, v.contact_name, v.contact_phone_encrypted, v.travel_mode, v.vehicle_plate,
                  v.submitted_time, 3, v.id
           FROM site_visit_invitation v JOIN project_info p ON p.id=v.project_id AND p.deleted=0
           WHERE v.wechat_app_id=#{appId} AND v.visitor_identity_hash=#{singleHash} AND v.deleted=0
             AND v.invite_type='SINGLE' AND v.status='SUBMITTED' AND v.submitted_time IS NOT NULL
             AND v.contact_phone_encrypted IS NOT NULL
           ORDER BY v.submitted_time DESC, v.id DESC LIMIT 1)
          UNION ALL
          (SELECT v.visitor_company, v.contact_name, v.contact_phone_encrypted, v.travel_mode, v.vehicle_plate,
                  v.registered_time, 2, v.id
           FROM site_meeting_visit_registration v
           JOIN site_visit_invitation m ON m.id=v.invitation_id AND m.project_id=v.project_id
             AND m.deleted=0 AND m.status='OPEN' AND m.invite_type='MEETING'
           JOIN project_info p ON p.id=v.project_id AND p.deleted=0
           WHERE v.wechat_app_id=#{appId} AND v.visitor_identity_hash=#{meetingHash} AND v.deleted=0
             AND v.status='REGISTERED' AND v.registered_time IS NOT NULL AND v.contact_phone_encrypted IS NOT NULL
           ORDER BY v.registered_time DESC, v.id DESC LIMIT 1)
          UNION ALL
          (SELECT v.visitor_company, v.contact_name, v.contact_phone_encrypted, v.travel_mode, v.vehicle_plate,
                  v.registered_time, 1, v.id
           FROM site_guard_visit_registration v JOIN project_info p ON p.id=v.project_id AND p.deleted=0
           WHERE v.wechat_app_id=#{appId} AND v.visitor_identity_hash=#{guardHash} AND v.deleted=0
             AND v.status='REGISTERED' AND v.registered_time IS NOT NULL AND v.contact_phone_encrypted IS NOT NULL
           ORDER BY v.registered_time DESC, v.id DESC LIMIT 1)
        ) recent
        ORDER BY submitted_time DESC, source_order DESC, source_id DESC LIMIT 1
        """)
    SiteVisitorPersonalProfile selectLatestPersonalInfo(@Param("appId") String appId,
            @Param("personalHash") String personalHash, @Param("singleHash") String singleHash,
            @Param("meetingHash") String meetingHash, @Param("guardHash") String guardHash);

    @Select("""
        SELECT v.visitor_company, v.contact_name, v.contact_phone_encrypted, v.travel_mode, v.vehicle_plate
        FROM site_visitor_profile v JOIN project_info p ON p.id=v.project_id AND p.deleted=0
        WHERE v.wechat_app_id=#{appId} AND v.owner_openid_hash=#{ownerHash}
          AND v.deleted=0 AND v.status='ACTIVE' AND v.contact_phone_encrypted IS NOT NULL
        ORDER BY COALESCE(v.last_used_time,v.create_time) DESC, v.id DESC LIMIT 1
        """)
    SiteVisitorPersonalProfile selectLatestNamedPersonalInfo(@Param("appId") String appId,
            @Param("ownerHash") String ownerHash);

    @Insert("INSERT INTO site_visitor_personal_profile_audit (profile_id,project_id,action,before_encrypted,after_encrypted,create_time) VALUES (#{profileId},#{projectId},#{action},#{before},#{after},CURRENT_TIMESTAMP)")
    int insertAudit(@Param("profileId") Long profileId, @Param("projectId") Long projectId,
            @Param("action") String action, @Param("before") String before, @Param("after") String after);
}
