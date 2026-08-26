package com.example.siteplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.quality.entity.QualityWeeklyInspectionDraftItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface QualityWeeklyInspectionDraftItemMapper
        extends BaseMapper<QualityWeeklyInspectionDraftItem> {

    @Select("""
            SELECT * FROM quality_weekly_inspection_draft_item
            WHERE inspection_id = #{inspectionId}
            ORDER BY item_order ASC, id ASC
            """)
    List<QualityWeeklyInspectionDraftItem> selectByInspectionId(
            @Param("inspectionId") Long inspectionId);

    @Update("""
            UPDATE quality_weekly_inspection_draft_item
            SET item_order = item_order + 1000, update_time = CURRENT_TIMESTAMP
            WHERE inspection_id = #{inspectionId}
            """)
    int moveOrdersOutOfRange(@Param("inspectionId") Long inspectionId);

    @Delete("DELETE FROM quality_weekly_inspection_draft_item WHERE inspection_id = #{inspectionId}")
    int deleteByInspectionId(@Param("inspectionId") Long inspectionId);
}
