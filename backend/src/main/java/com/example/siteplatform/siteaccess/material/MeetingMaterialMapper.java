package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MeetingMaterialMapper extends BaseMapper<MeetingMaterial> {
    @Select("SELECT * FROM site_meeting_material WHERE id = #{id} FOR UPDATE")
    MeetingMaterial lock(@Param("id") Long id);
}
