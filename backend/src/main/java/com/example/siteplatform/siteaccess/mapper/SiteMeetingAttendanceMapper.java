package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.siteaccess.entity.SiteMeetingAttendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SiteMeetingAttendanceMapper extends BaseMapper<SiteMeetingAttendance> {
    @Select("""
            SELECT p.id AS personId,p.person_name,p.person_company,a.checkin_time
            FROM site_meeting_attendance a
            JOIN site_meeting_visit_person p ON p.id=a.person_id AND p.deleted=0
            JOIN site_meeting_visit_registration r ON r.id=p.registration_id AND r.deleted=0 AND r.status='REGISTERED'
            WHERE a.invitation_id=#{invitationId} AND r.invitation_id=#{invitationId}
              AND a.status='CHECKED_IN' AND a.deleted=0
            ORDER BY a.checkin_time DESC,p.id DESC
            """)
    Page<com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO.Person> selectScreenPage(
            Page<com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO.Person> page,
            @Param("invitationId") Long invitationId);

    @Select("SELECT * FROM site_meeting_attendance WHERE person_id = #{personId} AND deleted = 0 FOR UPDATE")
    SiteMeetingAttendance selectByPersonForUpdate(@Param("personId") Long personId);

    @Select("SELECT COUNT(*) FROM site_meeting_attendance WHERE registration_id = #{registrationId} AND status = 'CHECKED_IN' AND deleted = 0")
    long countCheckedInByRegistration(@Param("registrationId") Long registrationId);

    @Select("""
            <script>
            SELECT p.id AS personId, p.registration_id AS registrationId,
                   r.registration_no AS registrationNo, r.registration_source AS registrationSource,
                   r.status AS registrationStatus, r.registered_time AS registeredTime,
                   r.travel_mode AS travelMode, r.vehicle_plate AS vehiclePlate,
                   p.person_type AS personType, p.person_company AS personCompany,
                   p.person_name AS personName, p.phone_encrypted AS phoneEncrypted, p.sort_order AS sortOrder,
                   a.id AS attendanceId, a.status AS attendanceStatus, a.checkin_method AS checkinMethod,
                   a.checkin_time AS checkinTime, a.location_result AS locationResult,
                   a.distance_meters AS distanceMeters, a.accuracy_meters AS accuracyMeters,
                   a.revoked_by_name AS revokedByName, a.revoked_time AS revokedTime,
                   a.revoke_reason AS revokeReason, COALESCE(a.version, 0) AS version
            FROM site_meeting_visit_person p
            JOIN site_meeting_visit_registration r ON r.id = p.registration_id AND r.deleted = 0
            LEFT JOIN site_meeting_attendance a ON a.person_id = p.id AND a.deleted = 0
            WHERE p.deleted = 0 AND r.invitation_id = #{invitationId} AND r.status = 'REGISTERED'
            <if test="registrationSource != null and registrationSource != ''">
              AND r.registration_source = #{registrationSource}
            </if>
            <if test="attendanceStatus != null and attendanceStatus == 'CHECKED_IN'">
              AND a.status = 'CHECKED_IN'
            </if>
            <if test="attendanceStatus != null and attendanceStatus == 'REVOKED'">
              AND a.status = 'REVOKED'
            </if>
            <if test="attendanceStatus != null and attendanceStatus == 'PENDING'">
              AND a.id IS NULL
            </if>
            <if test="locationResult != null and locationResult != ''">
              AND a.location_result = #{locationResult}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (r.registration_no LIKE CONCAT('%', #{keyword}, '%')
                   OR r.visitor_company LIKE CONCAT('%', #{keyword}, '%')
                   OR r.contact_name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.vehicle_plate LIKE CONCAT('%', #{keyword}, '%')
                   OR p.person_company LIKE CONCAT('%', #{keyword}, '%')
                   OR p.person_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY r.registered_time DESC, r.id DESC, p.sort_order ASC, p.id ASC
            </script>
            """)
    Page<Map<String, Object>> selectAttendeePage(Page<Map<String, Object>> page,
                                                  @Param("invitationId") Long invitationId,
                                                  @Param("registrationSource") String registrationSource,
                                                  @Param("attendanceStatus") String attendanceStatus,
                                                  @Param("locationResult") String locationResult,
                                                  @Param("keyword") String keyword);

    @Select("""
            SELECT p.id AS personId, p.registration_id AS registrationId,
                   r.registration_no AS registrationNo, r.registration_source AS registrationSource,
                   r.status AS registrationStatus, r.registered_time AS registeredTime,
                   r.travel_mode AS travelMode, r.vehicle_plate AS vehiclePlate,
                   p.person_type AS personType, p.person_company AS personCompany,
                   p.person_name AS personName, p.phone_encrypted AS phoneEncrypted, p.sort_order AS sortOrder,
                   a.id AS attendanceId, a.status AS attendanceStatus, a.checkin_method AS checkinMethod,
                   a.checkin_time AS checkinTime, a.location_result AS locationResult,
                   a.distance_meters AS distanceMeters, a.accuracy_meters AS accuracyMeters,
                   a.revoked_by_name AS revokedByName, a.revoked_time AS revokedTime,
                   a.revoke_reason AS revokeReason, COALESCE(a.version, 0) AS version
            FROM site_meeting_visit_person p
            JOIN site_meeting_visit_registration r ON r.id = p.registration_id AND r.deleted = 0
            LEFT JOIN site_meeting_attendance a ON a.person_id = p.id AND a.deleted = 0
            WHERE p.id = #{personId} AND p.deleted = 0
            """)
    Map<String, Object> selectAttendeeByPersonId(@Param("personId") Long personId);

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN r.registration_source = 'INVITATION' THEN 1 ELSE 0 END), 0) AS reservedPersonCount,
              COALESCE(SUM(CASE WHEN r.registration_source = 'INVITATION' AND a.status = 'CHECKED_IN' THEN 1 ELSE 0 END), 0) AS reservedCheckedInCount,
              COALESCE(SUM(CASE WHEN r.registration_source = 'WALK_IN' AND a.status = 'CHECKED_IN' THEN 1 ELSE 0 END), 0) AS walkInCheckedInCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' THEN 1 ELSE 0 END), 0) AS totalCheckedInCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' AND a.location_result = 'IN_RANGE' THEN 1 ELSE 0 END), 0) AS inRangeCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' AND a.location_result = 'OUT_OF_RANGE' THEN 1 ELSE 0 END), 0) AS outOfRangeCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' AND a.location_result = 'UNAVAILABLE' THEN 1 ELSE 0 END), 0) AS unavailableLocationCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' AND a.location_result = 'NO_REFERENCE' THEN 1 ELSE 0 END), 0) AS noReferenceLocationCount,
              COALESCE(SUM(CASE WHEN a.status = 'CHECKED_IN' AND a.location_result = 'MANUAL' THEN 1 ELSE 0 END), 0) AS manualLocationCount
            FROM site_meeting_visit_person p
            JOIN site_meeting_visit_registration r ON r.id = p.registration_id
              AND r.deleted = 0 AND r.status = 'REGISTERED'
            LEFT JOIN site_meeting_attendance a ON a.person_id = p.id AND a.deleted = 0
            WHERE p.deleted = 0 AND r.invitation_id = #{invitationId} AND r.status = 'REGISTERED'
            """)
    Map<String, Object> selectSummary(@Param("invitationId") Long invitationId);

    @Select("""
            SELECT p.id AS personId, p.registration_id AS registrationId,
                   r.registration_no AS registrationNo, r.registration_source AS registrationSource,
                   r.status AS registrationStatus, r.registered_time AS registeredTime,
                   r.travel_mode AS travelMode, r.vehicle_plate AS vehiclePlate,
                   p.person_type AS personType, p.person_company AS personCompany,
                   p.person_name AS personName, p.phone_encrypted AS phoneEncrypted, p.sort_order AS sortOrder,
                   a.status AS attendanceStatus, a.checkin_method AS checkinMethod,
                   a.checkin_time AS checkinTime, a.location_result AS locationResult,
                   a.distance_meters AS distanceMeters, a.accuracy_meters AS accuracyMeters,
                   a.revoked_by_name AS revokedByName, a.revoked_time AS revokedTime,
                   a.revoke_reason AS revokeReason, COALESCE(a.version, 0) AS version
            FROM site_meeting_visit_person p
            JOIN site_meeting_visit_registration r ON r.id = p.registration_id AND r.deleted = 0
            LEFT JOIN site_meeting_attendance a ON a.person_id = p.id AND a.deleted = 0
            WHERE p.deleted = 0 AND r.invitation_id = #{invitationId} AND r.status = 'REGISTERED'
            ORDER BY r.registered_time ASC, r.id ASC, p.sort_order ASC, p.id ASC
            LIMIT 50001
            """)
    List<Map<String, Object>> selectExportRows(@Param("invitationId") Long invitationId);
}
