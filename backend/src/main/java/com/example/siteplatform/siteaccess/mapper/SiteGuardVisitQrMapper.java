package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitQr;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteGuardVisitQrMapper extends BaseMapper<SiteGuardVisitQr> {
    @Select("SELECT * FROM site_guard_visit_qr WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteGuardVisitQr selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM site_guard_visit_qr WHERE project_id = #{projectId} AND qr_status IN ('ENABLED','DISABLED') AND deleted = 0 LIMIT 1 FOR UPDATE")
    SiteGuardVisitQr selectCurrentForUpdate(@Param("projectId") Long projectId);

    @Select("SELECT * FROM site_guard_visit_qr WHERE scene_token_hash = #{tokenHash} AND deleted = 0 LIMIT 1 FOR UPDATE")
    SiteGuardVisitQr selectForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
