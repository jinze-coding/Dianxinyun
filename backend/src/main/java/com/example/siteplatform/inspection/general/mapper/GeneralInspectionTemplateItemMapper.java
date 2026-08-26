package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTemplateItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GeneralInspectionTemplateItemMapper extends BaseMapper<GeneralInspectionTemplateItem> {
    @Delete("DELETE FROM general_inspection_template_item WHERE template_id=#{templateId} AND template_version_id IS NULL")
    int deleteDraftItems(@Param("templateId") Long templateId);
}
