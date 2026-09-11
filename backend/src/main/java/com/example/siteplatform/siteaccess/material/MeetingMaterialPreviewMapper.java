package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MeetingMaterialPreviewMapper extends BaseMapper<MeetingMaterialPreview> {
    @Select("SELECT * FROM site_meeting_material_preview WHERE id = #{id} FOR UPDATE")
    MeetingMaterialPreview lock(@Param("id") Long id);
}
