package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteMeetingCheckinQr;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteMeetingCheckinQrMapper extends BaseMapper<SiteMeetingCheckinQr> {
    @Select("SELECT * FROM site_meeting_checkin_qr WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteMeetingCheckinQr selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM site_meeting_checkin_qr
            WHERE invitation_id = #{invitationId} AND qr_status IN ('ENABLED', 'DISABLED') AND deleted = 0
            ORDER BY qr_version DESC, id DESC LIMIT 1
            """)
    SiteMeetingCheckinQr selectCurrent(@Param("invitationId") Long invitationId);

    @Select("""
            SELECT * FROM site_meeting_checkin_qr
            WHERE invitation_id = #{invitationId} AND qr_status IN ('ENABLED', 'DISABLED') AND deleted = 0
            ORDER BY qr_version DESC, id DESC LIMIT 1 FOR UPDATE
            """)
    SiteMeetingCheckinQr selectCurrentForUpdate(@Param("invitationId") Long invitationId);
}
