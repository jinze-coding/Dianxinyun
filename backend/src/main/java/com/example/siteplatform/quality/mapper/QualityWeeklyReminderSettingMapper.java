package com.example.siteplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.quality.entity.QualityWeeklyReminderSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface QualityWeeklyReminderSettingMapper extends BaseMapper<QualityWeeklyReminderSetting> {

    @Select("SELECT * FROM quality_weekly_reminder_setting WHERE project_id = #{projectId} LIMIT 1")
    QualityWeeklyReminderSetting selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM quality_weekly_reminder_setting WHERE project_id = #{projectId} LIMIT 1 FOR UPDATE")
    QualityWeeklyReminderSetting selectByProjectIdForUpdate(@Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM quality_weekly_reminder_setting
            WHERE project_id = (
                SELECT project_id FROM quality_weekly_inspection WHERE id = #{inspectionId} LIMIT 1
            )
            LIMIT 1 FOR UPDATE
            """)
    QualityWeeklyReminderSetting selectByInspectionIdForUpdate(@Param("inspectionId") Long inspectionId);

    @Select("""
            SELECT * FROM quality_weekly_reminder_setting
            WHERE enabled = 1 AND responsible_user_id IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM quality_weekly_inspection inspection
                WHERE inspection.project_id = quality_weekly_reminder_setting.project_id
                  AND inspection.week_start = #{weekStart}
                  AND inspection.status <> 'DRAFT'
              )
              AND NOT EXISTS (
                SELECT 1 FROM user_notification notification
                WHERE notification.dedup_key = CONCAT(
                    'reminder:qweek:', quality_weekly_reminder_setting.project_id, ':', #{weekStart}
                )
              )
            ORDER BY project_id
            """)
    List<QualityWeeklyReminderSetting> selectEnabledSettings(
            @Param("weekStart") LocalDate weekStart);

    @Update("""
            UPDATE quality_weekly_reminder_setting
            SET enabled = #{enabled}, day_of_week = #{dayOfWeek}, trigger_time = #{triggerTime},
                responsible_user_id = #{responsibleUserId},
                responsible_user_name = #{responsibleUserName},
                effective_time = #{effectiveTime}, version = version + 1,
                updated_by_id = #{updatedById}, updated_by_name = #{updatedByName},
                update_time = #{updateTime}
            WHERE id = #{id} AND version = #{expectedVersion}
            """)
    int updateSetting(@Param("id") Long id,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("enabled") Integer enabled,
                      @Param("dayOfWeek") Integer dayOfWeek,
                      @Param("triggerTime") LocalTime triggerTime,
                      @Param("responsibleUserId") Long responsibleUserId,
                      @Param("responsibleUserName") String responsibleUserName,
                      @Param("effectiveTime") LocalDateTime effectiveTime,
                      @Param("updatedById") Long updatedById,
                      @Param("updatedByName") String updatedByName,
                      @Param("updateTime") LocalDateTime updateTime);
}
