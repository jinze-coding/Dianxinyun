package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MeetingMaterialVersionMapper extends BaseMapper<MeetingMaterialVersion> {
    @Select("SELECT * FROM site_meeting_material_version WHERE id = #{id} FOR UPDATE")
    MeetingMaterialVersion lock(@Param("id") Long id);
}
