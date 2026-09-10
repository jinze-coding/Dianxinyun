package com.example.siteplatform.siteaccess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitPerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteMeetingVisitPersonMapper extends BaseMapper<SiteMeetingVisitPerson> {
    @Select("SELECT * FROM site_meeting_visit_person WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SiteMeetingVisitPerson selectForUpdate(@Param("id") Long id);
}
