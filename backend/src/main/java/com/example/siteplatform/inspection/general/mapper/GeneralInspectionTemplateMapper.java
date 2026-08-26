package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GeneralInspectionTemplateMapper extends BaseMapper<GeneralInspectionTemplate> {
    @Select("SELECT * FROM general_inspection_template WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GeneralInspectionTemplate selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE general_inspection_template
            SET template_name=#{templateName}, category_name=#{categoryName},
                overall_photo_min=#{overallPhotoMin}, overall_photo_max=#{overallPhotoMax},
                overall_remark_required=#{overallRemarkRequired}, remark=#{remark},
                updated_by_id=#{updatedById}, updated_by_name=#{updatedByName},
                version=version+1, update_time=CURRENT_TIMESTAMP
            WHERE id=#{id} AND version=#{expectedVersion} AND deleted=0 AND status<>'ARCHIVED'
            """)
    int updateDraft(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                    @Param("templateName") String templateName, @Param("categoryName") String categoryName,
                    @Param("overallPhotoMin") Integer overallPhotoMin,
                    @Param("overallPhotoMax") Integer overallPhotoMax,
                    @Param("overallRemarkRequired") Integer overallRemarkRequired,
                    @Param("remark") String remark, @Param("updatedById") Long updatedById,
                    @Param("updatedByName") String updatedByName);
}
