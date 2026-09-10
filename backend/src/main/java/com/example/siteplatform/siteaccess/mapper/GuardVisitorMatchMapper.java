package com.example.siteplatform.siteaccess.mapper;

import com.example.siteplatform.siteaccess.vo.PublicGuardMatchedPassVO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GuardVisitorMatchMapper {
    @Select("""
        SELECT id AS sourceId, 'SINGLE' AS sourceType, invite_no AS sourceNo, purpose AS subject,
               visitor_company, contact_name, visitor_count, travel_mode, vehicle_plate,
               visit_start_time, visit_end_time AS validUntil
        FROM site_visit_invitation
        WHERE project_id=#{projectId} AND wechat_app_id=#{appId} AND visitor_identity_hash=#{identity}
          AND deleted=0 AND invite_type='SINGLE' AND status='SUBMITTED'
          AND visit_start_time<=#{now} AND visit_end_time>#{now}
        ORDER BY visit_start_time,id
        """)
    List<PublicGuardMatchedPassVO> single(@Param("projectId") Long projectId, @Param("appId") String appId,
            @Param("identity") String identity, @Param("now") LocalDateTime now);

    @Select("""
        SELECT r.id AS sourceId, 'MEETING' AS sourceType, r.registration_no AS sourceNo, i.purpose AS subject,
               r.visitor_company,r.contact_name,r.visitor_count,r.travel_mode,r.vehicle_plate,
               i.visit_start_time,i.visit_end_time AS validUntil
        FROM site_meeting_visit_registration r
        JOIN site_visit_invitation i ON i.id=r.invitation_id AND i.project_id=r.project_id
        WHERE r.project_id=#{projectId} AND r.wechat_app_id=#{appId} AND r.visitor_identity_hash=#{identity}
          AND r.deleted=0 AND r.status='REGISTERED' AND i.deleted=0 AND i.invite_type='MEETING' AND i.status='OPEN'
          AND i.visit_start_time<=#{now} AND i.visit_end_time>#{now}
        ORDER BY i.visit_start_time,r.id
        """)
    List<PublicGuardMatchedPassVO> meeting(@Param("projectId") Long projectId, @Param("appId") String appId,
            @Param("identity") String identity, @Param("now") LocalDateTime now);

    @Select("SELECT person_type,person_company,person_name FROM site_visit_person WHERE invitation_id=#{id} AND project_id=#{projectId} AND deleted=0 ORDER BY sort_order,id")
    List<PublicGuardMatchedPassVO.Person> singlePeople(@Param("id") Long id, @Param("projectId") Long projectId);

    @Select("SELECT person_type,person_company,person_name FROM site_meeting_visit_person WHERE registration_id=#{id} AND project_id=#{projectId} AND deleted=0 ORDER BY sort_order,id")
    List<PublicGuardMatchedPassVO.Person> meetingPeople(@Param("id") Long id, @Param("projectId") Long projectId);
}
