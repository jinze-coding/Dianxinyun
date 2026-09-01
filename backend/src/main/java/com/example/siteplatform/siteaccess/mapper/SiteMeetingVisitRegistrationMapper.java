package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SiteMeetingVisitRegistrationMapper extends BaseMapper<SiteMeetingVisitRegistration> {
    @Select("SELECT * FROM site_meeting_visit_registration WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteMeetingVisitRegistration selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM site_meeting_visit_registration
            WHERE invitation_id = #{invitationId} AND wechat_app_id = #{appId}
              AND visitor_identity_hash = #{identityHash} AND status = 'REGISTERED' AND deleted = 0
            ORDER BY registered_time DESC, id DESC LIMIT 1 FOR UPDATE
            """)
    SiteMeetingVisitRegistration selectActiveForUpdate(@Param("invitationId") Long invitationId,
                                                        @Param("appId") String appId,
                                                        @Param("identityHash") String identityHash);

    @Select("""
            <script>
            SELECT invitation_id AS invitationId,
                   COUNT(*) AS registrationGroupCount,
                   COALESCE(SUM(visitor_count), 0) AS registeredPersonCount
            FROM site_meeting_visit_registration
            WHERE deleted = 0 AND status = 'REGISTERED' AND invitation_id IN
            <foreach collection="invitationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY invitation_id
            </script>
            """)
    List<Map<String, Object>> selectActiveStats(@Param("invitationIds") List<Long> invitationIds);
}
