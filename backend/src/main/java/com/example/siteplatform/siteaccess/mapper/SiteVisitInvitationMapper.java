package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteVisitInvitationMapper extends BaseMapper<SiteVisitInvitation> {
    @Select("SELECT * FROM site_visit_invitation WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteVisitInvitation selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM site_visit_invitation WHERE token_hash = #{tokenHash} AND deleted = 0 LIMIT 1 FOR UPDATE")
    SiteVisitInvitation selectForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
