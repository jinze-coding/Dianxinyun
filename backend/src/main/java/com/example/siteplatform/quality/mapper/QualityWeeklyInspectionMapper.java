package com.example.siteplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface QualityWeeklyInspectionMapper extends BaseMapper<QualityWeeklyInspection> {

    @Select("""
            SELECT * FROM quality_weekly_inspection
            WHERE project_id = #{projectId} AND week_start = #{weekStart}
            LIMIT 1
            """)
    QualityWeeklyInspection selectByProjectWeek(@Param("projectId") Long projectId,
                                                @Param("weekStart") LocalDate weekStart);

    @Select("""
            SELECT * FROM quality_weekly_inspection
            WHERE project_id = #{projectId} AND week_start = #{weekStart}
            LIMIT 1 FOR UPDATE
            """)
    QualityWeeklyInspection selectByProjectWeekForUpdate(@Param("projectId") Long projectId,
                                                         @Param("weekStart") LocalDate weekStart);

    @Select("SELECT * FROM quality_weekly_inspection WHERE id = #{id} FOR UPDATE")
    QualityWeeklyInspection selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE quality_weekly_inspection
            SET inspection_date = #{inspectionDate}, conclusion = #{conclusion},
                last_edited_by_id = #{editorId}, last_edited_by_name = #{editorName},
                version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'DRAFT' AND version = #{expectedVersion}
            """)
    int updateDraft(@Param("id") Long id,
                    @Param("expectedVersion") Integer expectedVersion,
                    @Param("inspectionDate") LocalDate inspectionDate,
                    @Param("conclusion") String conclusion,
                    @Param("editorId") Long editorId,
                    @Param("editorName") String editorName,
                    @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE quality_weekly_inspection
            SET inspection_no = #{inspectionNo}, status = 'SUBMITTED',
                submitted_issue_count = #{submittedIssueCount},
                submitted_by_id = #{submittedById}, submitted_by_name = #{submittedByName},
                submitted_time = #{submittedTime}, last_edited_by_id = #{submittedById},
                last_edited_by_name = #{submittedByName}, version = version + 1,
                update_time = #{submittedTime}
            WHERE id = #{id} AND status = 'DRAFT' AND version = #{expectedVersion}
            """)
    int submit(@Param("id") Long id,
               @Param("expectedVersion") Integer expectedVersion,
               @Param("inspectionNo") String inspectionNo,
               @Param("submittedIssueCount") Integer submittedIssueCount,
               @Param("submittedById") Long submittedById,
               @Param("submittedByName") String submittedByName,
               @Param("submittedTime") LocalDateTime submittedTime);

    @Delete("""
            DELETE FROM quality_weekly_inspection
            WHERE id = #{id} AND status = 'DRAFT' AND version = #{expectedVersion}
            """)
    int deleteDraft(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion);
}
